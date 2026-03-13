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

package io.zeromagic.doorman.traffic;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressRule;
import io.fabric8.kubernetes.api.model.networking.v1.IngressServiceBackend;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.events.ScalingPolicyEvents;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Resolves the Traefik-internal service label for a Kubernetes service.
 *
 * <p>Traefik names each backend as {@code {namespace}-{serviceName}-{port}@kubernetes}.
 * The port is obtained from the Kubernetes Ingress whose name is recorded in
 * {@link io.zeromagic.doorman.repository.crd.ScalingPolicySpec#getIngressName()}.
 *
 * <p>Implements {@link ScalingPolicyEvents} to maintain a local index of
 * {@code (namespace, serviceName) → ingressName} and to invalidate the resolved-label
 * cache whenever a policy changes.
 */
@Singleton
class TraefikServiceNameResolver implements ScalingPolicyEvents {

    private static final Logger LOG = LoggerFactory.getLogger(TraefikServiceNameResolver.class);

    private final KubernetesFacade facade;

    /** Maps "{namespace}/{serviceName}" → ingressName, populated from ScalingPolicy events. */
    private final ConcurrentHashMap<String, String> policyIndex = new ConcurrentHashMap<>();

    /** Cache: "{namespace}/{serviceName}" → resolved Traefik label (or empty on lookup failure). */
    private final ConcurrentHashMap<String, Optional<String>> cache = new ConcurrentHashMap<>();

    @Inject
    TraefikServiceNameResolver(KubernetesFacade facade) {
        this.facade = facade;
    }

    // ── ScalingPolicyEvents ───────────────────────────────────────────────────

    @Override
    public void onAdded(ScalingPolicy policy) {
        String key = key(policy.getMetadata().getNamespace(), policy.getSpec().getServiceName());
        policyIndex.put(key, policy.getSpec().getIngressName());
    }

    @Override
    public void onUpdated(ScalingPolicy oldPolicy, ScalingPolicy newPolicy) {
        String key = key(newPolicy.getMetadata().getNamespace(), newPolicy.getSpec().getServiceName());
        policyIndex.put(key, newPolicy.getSpec().getIngressName());
        cache.remove(key);
    }

    @Override
    public void onDeleted(ScalingPolicy policy) {
        String key = key(policy.getMetadata().getNamespace(), policy.getSpec().getServiceName());
        policyIndex.remove(key);
        cache.remove(key);
    }

    // ── Resolution ────────────────────────────────────────────────────────────

    /**
     * Resolves the Traefik service label for the given service.
     * Results are cached; the cache is invalidated when the corresponding ScalingPolicy is updated.
     *
     * @return the label string, or empty if the Ingress is missing or does not reference the service
     */
    public Optional<String> resolve(String namespace, String serviceName) {
        return cache.computeIfAbsent(key(namespace, serviceName),
                k -> lookupLabel(namespace, serviceName));
    }

    private Optional<String> lookupLabel(String namespace, String serviceName) {
        String ingressName = policyIndex.get(key(namespace, serviceName));
        if (ingressName == null || ingressName.isBlank()) {
            LOG.warn("No ingressName known for {}/{} — ScalingPolicy not loaded yet?",
                    namespace, serviceName);
            return Optional.empty();
        }

        Optional<Ingress> ingressOpt = facade.getIngress(namespace, ingressName);
        if (ingressOpt.isEmpty()) {
            LOG.warn("Ingress {}/{} not found while resolving Traefik label for service {}",
                    namespace, ingressName, serviceName);
            return Optional.empty();
        }

        Ingress ingress = ingressOpt.get();
        var spec = ingress.getSpec();
        if (spec == null) {
            LOG.warn("Ingress {}/{} has no spec", namespace, ingressName);
            return Optional.empty();
        }

        // Check default backend first
        if (spec.getDefaultBackend() != null) {
            Optional<String> label = portFromBackendService(spec.getDefaultBackend().getService(),
                    namespace, serviceName);
            if (label.isPresent()) return label;
        }

        // Search through all rules and paths
        List<IngressRule> rules = spec.getRules();
        if (rules != null) {
            for (var rule : rules) {
                if (rule.getHttp() == null) continue;
                for (var path : rule.getHttp().getPaths()) {
                    if (path.getBackend() == null) continue;
                    Optional<String> label = portFromBackendService(
                            path.getBackend().getService(), namespace, serviceName);
                    if (label.isPresent()) return label;
                }
            }
        }

        LOG.warn("Service {} not found in Ingress {}/{}", serviceName, namespace, ingressName);
        return Optional.empty();
    }

    private Optional<String> portFromBackendService(
            IngressServiceBackend svc, String namespace, String serviceName) {
        if (svc == null) return Optional.empty();
        if (!serviceName.equals(svc.getName())) return Optional.empty();
        if (svc.getPort() == null || svc.getPort().getNumber() == null) return Optional.empty();
        return Optional.of(buildLabel(namespace, serviceName, svc.getPort().getNumber()));
    }

    private static String buildLabel(String namespace, String serviceName, int port) {
        return namespace + "-" + serviceName + "-" + port + "@kubernetes";
    }

    private static String key(String namespace, String serviceName) {
        return namespace + "/" + serviceName;
    }
}
