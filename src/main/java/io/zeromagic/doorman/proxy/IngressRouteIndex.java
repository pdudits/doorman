/*
 * Copyright © 2026 Doorman contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.zeromagic.doorman.proxy;

import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.events.ScalingPolicyEvents;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a routing table that maps (Host header, path prefix) to the Kubernetes
 * (namespace, serviceName) that should handle the request.
 *
 * <p>The table is populated from the Ingress resources referenced by active ScalingPolicies.
 * Only rules whose backend service matches the ScalingPolicy's {@code serviceName} are indexed.
 * Default backends (which have no hostname) are intentionally skipped.
 *
 * <p>Thread-safe: the outer map is a {@link ConcurrentHashMap}; per-host concurrency is managed
 * by {@link HostRoutes}.
 */
@Singleton
public class IngressRouteIndex implements ScalingPolicyEvents {

    private static final Logger LOG = LoggerFactory.getLogger(IngressRouteIndex.class);

    private record RouteKey(String host, String pathPrefix) {}

    private final KubernetesFacade facade;

    /** hostname → sorted route entries */
    private final ConcurrentHashMap<String, HostRoutes> byHost = new ConcurrentHashMap<>();

    /** policyKey → route keys added for that policy (for clean removal) */
    private final ConcurrentHashMap<String, List<RouteKey>> byPolicy = new ConcurrentHashMap<>();

    @Inject
    public IngressRouteIndex(KubernetesFacade facade) {
        this.facade = facade;
    }

    // ── ScalingPolicyEvents ───────────────────────────────────────────────────

    @Override
    public void onAdded(ScalingPolicy policy) {
        index(policy);
    }

    @Override
    public void onUpdated(ScalingPolicy oldPolicy, ScalingPolicy newPolicy) {
        removePolicy(oldPolicy);
        index(newPolicy);
    }

    @Override
    public void onDeleted(ScalingPolicy policy) {
        removePolicy(policy);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Resolves an incoming HTTP request to a {@link RouteTarget}.
     *
     * @param host the value of the HTTP {@code Host} header (port suffix is stripped if present)
     * @param path the request URI path
     * @return the matching target, or empty if no route matches
     */
    public Optional<RouteTarget> resolve(String host, String path) {
        String bareHost = stripPort(host);
        HostRoutes routes = byHost.get(bareHost);
        if (routes == null) {
            return Optional.empty();
        }
        return routes.resolve(path);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void index(ScalingPolicy policy) {
        var ns = policy.getMetadata().getNamespace();
        var spec = policy.getSpec();
        var serviceName = spec.getServiceName();
        var ingressName = spec.getIngressName();

        if (ingressName == null || ingressName.isBlank()) {
            LOG.warn("ScalingPolicy {}/{} has no ingressName — skipping route indexing", ns, serviceName);
            return;
        }

        var ingressOpt = facade.getIngress(ns, ingressName);
        if (ingressOpt.isEmpty()) {
            LOG.warn("Ingress {}/{} not found for ScalingPolicy {}/{} — skipping route indexing",
                    ns, ingressName, ns, serviceName);
            return;
        }

        var ingressSpec = ingressOpt.get().getSpec();
        if (ingressSpec == null || ingressSpec.getRules() == null) {
            return;
        }

        var policyKey = policyKey(ns, serviceName);
        var addedKeys = new ArrayList<RouteKey>();

        for (var rule : ingressSpec.getRules()) {
            var ruleHost = rule.getHost();
            if (ruleHost == null || ruleHost.isBlank() || rule.getHttp() == null) {
                continue;
            }
            for (var path : rule.getHttp().getPaths()) {
                if (path.getBackend() == null
                        || path.getBackend().getService() == null
                        || !serviceName.equals(path.getBackend().getService().getName())) {
                    continue;
                }
                var pathPrefix = path.getPath();
                if (pathPrefix == null) {
                    continue;
                }
                var target = new RouteTarget(ns, serviceName);
                byHost.computeIfAbsent(ruleHost, h -> new HostRoutes()).add(pathPrefix, target);
                addedKeys.add(new RouteKey(ruleHost, pathPrefix));
            }
        }

        byPolicy.put(policyKey, addedKeys);
    }

    private void removePolicy(ScalingPolicy policy) {
        var ns = policy.getMetadata().getNamespace();
        var serviceName = policy.getSpec().getServiceName();
        var keys = byPolicy.remove(policyKey(ns, serviceName));
        if (keys == null) {
            return;
        }
        for (var key : keys) {
            var routes = byHost.get(key.host());
            if (routes != null) {
                routes.remove(key.pathPrefix());
                if (routes.isEmpty()) {
                    byHost.remove(key.host(), routes);
                }
            }
        }
    }

    private static String stripPort(String host) {
        if (host == null) return "";
        int colon = host.lastIndexOf(':');
        return colon >= 0 ? host.substring(0, colon) : host;
    }

    private static String policyKey(String namespace, String serviceName) {
        return namespace + "/" + serviceName;
    }
}
