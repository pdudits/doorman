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

package io.zeromagic.doorman.repository;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.zeromagic.doorman.cli.DurationParser;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ScalingPolicyStatusPatcher;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicyPhase;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of all managed applications. Implements all four watcher
 * event interfaces and delegates state transitions to {@link ScaledApplication}.
 * After each transition this class calls the appropriate side-effect interface:
 * {@link ScalingPolicyStatusPatcher}, {@link ServiceScaler}, or {@link EndpointRegistrar}.
 */
@Singleton
public class ScaledApplicationRegistry
        implements ScalingPolicyEvents, DeploymentEvents, EndpointsEvents, EndpointSliceEvents {

    private static final Logger LOG = LoggerFactory.getLogger(ScaledApplicationRegistry.class);

    private final ConcurrentHashMap<String, ScaledApplication> byPolicyKey = new ConcurrentHashMap<>();
    /** namespace/deploymentName → policyKey */
    private final ConcurrentHashMap<String, String> deploymentIndex = new ConcurrentHashMap<>();
    /** namespace/serviceName → policyKey */
    private final ConcurrentHashMap<String, String> serviceIndex = new ConcurrentHashMap<>();

    private final ScalingPolicyStatusPatcher statusPatcher;
    private final ServiceScaler scaler;
    private final EndpointRegistrar registrar;
    private final DeploymentStateReader deploymentReader;
    private final Duration globalIdleTimeout;

    @jakarta.inject.Inject
    public ScaledApplicationRegistry(ScalingPolicyStatusPatcher statusPatcher,
                                      ServiceScaler scaler,
                                      EndpointRegistrar registrar,
                                      DeploymentStateReader deploymentReader,
                                      TraefikConfig traefikConfig) {
        this(statusPatcher, scaler, registrar, deploymentReader,
                DurationParser.parse(traefikConfig.idleTimeout()));
    }

    /** Test constructor — accepts a pre-parsed global idle timeout. */
    public ScaledApplicationRegistry(ScalingPolicyStatusPatcher statusPatcher,
                                      ServiceScaler scaler,
                                      EndpointRegistrar registrar,
                                      DeploymentStateReader deploymentReader,
                                      Duration globalIdleTimeout) {
        this.statusPatcher = statusPatcher;
        this.scaler = scaler;
        this.registrar = registrar;
        this.deploymentReader = deploymentReader;
        this.globalIdleTimeout = globalIdleTimeout;
    }

    // -------------------------------------------------------------------------
    // ScalingPolicyEvents
    // -------------------------------------------------------------------------

    @Override
    public void onAdded(ScalingPolicy policy) {
        var meta = policy.getMetadata();
        var spec = policy.getSpec();
        var policyKey = policyKey(meta.getNamespace(), meta.getName());

        var deployState = deploymentReader.read(meta.getNamespace(), spec.getDeploymentName());
        var existingPhase = policy.getStatus() != null ? policy.getStatus().getPhase() : null;
        var existingTarget = policy.getStatus() != null ? policy.getStatus().getTargetReplicas() : null;

        ServiceState initialState = determineInitialState(deployState, existingPhase);
        int targetReplicas = resolveTargetReplicas(existingTarget, deployState);

        var snapshot = new ScaledApplication.Snapshot(
                meta.getNamespace(), meta.getName(),
                spec.getServiceName(), spec.getDeploymentName(),
                targetReplicas,
                resolveIdleTimeout(spec.getIdleTimeout()));

        var app = new ScaledApplication(snapshot, initialState);
        byPolicyKey.put(policyKey, app);
        deploymentIndex.put(deploymentKey(meta.getNamespace(), spec.getDeploymentName()), policyKey);
        serviceIndex.put(serviceKey(meta.getNamespace(), spec.getServiceName()), policyKey);

        patchStatus(app);
        LOG.info("Registered {}/{} → {} (target={})",
                meta.getNamespace(), meta.getName(),
                initialState.getClass().getSimpleName(), targetReplicas);
    }

    @Override
    public void onUpdated(ScalingPolicy oldPolicy, ScalingPolicy newPolicy) {
        var meta = newPolicy.getMetadata();
        var app = byPolicyKey.get(policyKey(meta.getNamespace(), meta.getName()));
        if (app == null) return;
        var spec = newPolicy.getSpec();
        var old = app.snapshot();
        if (!old.serviceName().equals(spec.getServiceName())
                || !old.deploymentName().equals(spec.getDeploymentName())) {
            // Spec changed — reindex
            deploymentIndex.remove(deploymentKey(old.namespace(), old.deploymentName()));
            serviceIndex.remove(serviceKey(old.namespace(), old.serviceName()));
            var newSnap = new ScaledApplication.Snapshot(
                    meta.getNamespace(), meta.getName(),
                    spec.getServiceName(), spec.getDeploymentName(),
                    old.targetReplicas(),
                    resolveIdleTimeout(spec.getIdleTimeout()));
            app.updateSnapshot(newSnap);
            deploymentIndex.put(deploymentKey(meta.getNamespace(), spec.getDeploymentName()),
                    policyKey(meta.getNamespace(), meta.getName()));
            serviceIndex.put(serviceKey(meta.getNamespace(), spec.getServiceName()),
                    policyKey(meta.getNamespace(), meta.getName()));
            LOG.info("Updated spec for {}/{}", meta.getNamespace(), meta.getName());
        }
    }

    @Override
    public void onDeleted(ScalingPolicy policy) {
        var meta = policy.getMetadata();
        var policyKey = policyKey(meta.getNamespace(), meta.getName());
        var app = byPolicyKey.remove(policyKey);
        if (app == null) return;
        deploymentIndex.remove(deploymentKey(meta.getNamespace(), app.snapshot().deploymentName()));
        serviceIndex.remove(serviceKey(meta.getNamespace(), app.snapshot().serviceName()));
        app.cancelPendingFuture();
        LOG.info("Unregistered {}/{}", meta.getNamespace(), meta.getName());
    }

    // -------------------------------------------------------------------------
    // DeploymentEvents
    // -------------------------------------------------------------------------

    @Override
    public void onDeploymentChanged(Deployment deployment) {
        var meta = deployment.getMetadata();
        var key = deploymentIndex.get(deploymentKey(meta.getNamespace(), meta.getName()));
        if (key == null) return;
        var app = byPolicyKey.get(key);
        if (app == null) return;

        int ready = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas() : 0;

        if (ready == 0) {
            if (app.confirmScaledDown()) {
                patchStatus(app);
            }
        } else if (ready >= 1) {
            if (app.confirmRunning()) {
                patchStatus(app);
            }
        }
    }

    // -------------------------------------------------------------------------
    // EndpointsEvents (classic Endpoints API — Traefik v2)
    // -------------------------------------------------------------------------

    @Override
    public void onRealEndpointsDrained(String namespace, String serviceName) {
        withApp(serviceKey(namespace, serviceName), app -> {
            if (app.confirmScaledDown()) {
                patchStatus(app);
            }
        });
    }

    @Override
    public void onDoormanEndpointRemoved(String namespace, String serviceName) {
        withApp(serviceKey(namespace, serviceName), app -> {
            switch (app.currentState()) {
                case ServiceState.ScaledDown ignored -> registrar.register(namespace, serviceName);
                case ServiceState.ScalingUp ignored -> registrar.register(namespace, serviceName);
                default -> {} // not our fight to pick right now
            }
        });
    }

    @Override
    public void onRealEndpointsReady(String namespace, String serviceName) {
        LOG.debug("Real endpoints ready for {}/{} (deployment event will confirm)", namespace, serviceName);
    }

    // EndpointSliceEvents (Traefik v3+) has distinct method names ("Slice" suffix)
    // but the same coordination logic. The actual fight-back K8s operations differ
    // at the EndpointRegistrar implementation level (Task-006).

    @Override
    public void onRealSlicesDrained(String namespace, String serviceName) {
        withApp(serviceKey(namespace, serviceName), app -> {
            if (app.confirmScaledDown()) {
                patchStatus(app);
            }
        });
    }

    @Override
    public void onDoormanSliceRemoved(String namespace, String serviceName) {
        withApp(serviceKey(namespace, serviceName), app -> {
            switch (app.currentState()) {
                case ServiceState.ScaledDown ignored -> registrar.register(namespace, serviceName);
                case ServiceState.ScalingUp ignored -> registrar.register(namespace, serviceName);
                default -> {}
            }
        });
    }

    @Override
    public void onRealSlicesReady(String namespace, String serviceName) {
        LOG.debug("Real endpoint slices ready for {}/{} (deployment event will confirm)", namespace, serviceName);
    }

    // -------------------------------------------------------------------------
    // Proxy entry point
    // -------------------------------------------------------------------------

    /**
     * Called by the HTTP proxy when a request arrives for a managed service.
     * Returns a future that completes with the redirect URL once the service is ready.
     */
    public CompletableFuture<Void> awaitReady(String namespace, String serviceName) {
        var key = serviceIndex.get(serviceKey(namespace, serviceName));
        if (key == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("No ScalingPolicy found for " + namespace + "/" + serviceName));
        }
        var app = byPolicyKey.get(key);
        if (app == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Registry inconsistency for " + namespace + "/" + serviceName));
        }
        var result = app.awaitReady();
        if (result.scaleUpNeeded()) {
            var snap = app.snapshot();
            scaler.scaleUp(snap.namespace(), snap.deploymentName(), snap.targetReplicas());
        }
        return result.future();
    }

    // -------------------------------------------------------------------------
    // IdleDetector entry point
    // -------------------------------------------------------------------------

    /**
     * Called by the idle detector when a service has been idle long enough to scale down.
     * Transitions {@code Running → ScalingDown} and patches the ScalingPolicy status.
     * No-op (with a warning) if the service is not managed.
     */
    public void beginScalingDown(String namespace, String serviceName) {
        withApp(serviceKey(namespace, serviceName), app -> {
            if (app.beginScalingDown()) {
                patchStatus(app);
            }
        }, () -> LOG.warn("beginScalingDown called for unmanaged service {}/{}", namespace, serviceName));
    }

    // -------------------------------------------------------------------------
    // Lookups
    // -------------------------------------------------------------------------

    public Optional<ScaledApplication> byServiceName(String namespace, String serviceName) {
        return Optional.ofNullable(serviceIndex.get(serviceKey(namespace, serviceName)))
                .map(byPolicyKey::get);
    }

    public Collection<ScaledApplication> all() {
        return byPolicyKey.values();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void patchStatus(ScaledApplication app) {
        var snap = app.snapshot();
        statusPatcher.patch(snap.namespace(), snap.policyName(),
                toPhase(app.currentState()), snap.targetReplicas(), null);
    }

    private void withApp(String indexKey, java.util.function.Consumer<ScaledApplication> action) {
        withApp(indexKey, action, () -> {});
    }

    private void withApp(String indexKey, java.util.function.Consumer<ScaledApplication> action, Runnable notFound) {
        var policyKey = serviceIndex.get(indexKey);
        if (policyKey == null) {
            notFound.run();
            return;
        }
        var app = byPolicyKey.get(policyKey);
        if (app != null) action.accept(app);
    }

    private Duration resolveIdleTimeout(String policyIdleTimeout) {
        if (policyIdleTimeout != null && !policyIdleTimeout.isBlank()) {
            return DurationParser.parse(policyIdleTimeout);
        }
        return globalIdleTimeout;
    }

    private static ScalingPolicyPhase toPhase(ServiceState state) {
        return switch (state) {
            case ServiceState.Running() -> ScalingPolicyPhase.Running;
            case ServiceState.ScalingDown() -> ScalingPolicyPhase.ScalingDown;
            case ServiceState.ScaledDown ignored -> ScalingPolicyPhase.ScaledDown;
            case ServiceState.ScalingUp ignored -> ScalingPolicyPhase.ScalingUp;
            case ServiceState.Stopped() -> ScalingPolicyPhase.Stopped;
        };
    }

    private static String policyKey(String namespace, String name) { return namespace + "/" + name; }
    private static String deploymentKey(String namespace, String name) { return namespace + "/" + name; }
    private static String serviceKey(String namespace, String name) { return namespace + "/" + name; }

    private static ServiceState determineInitialState(
            Optional<DeploymentStateReader.DeploymentState> deployState,
            ScalingPolicyPhase existingPhase) {
        if (deployState.isEmpty()) {
            return new ServiceState.Stopped();
        }
        var ds = deployState.get();
        if (ds.specReplicas() == 0) {
            return (existingPhase == ScalingPolicyPhase.ScaledDown
                    || existingPhase == ScalingPolicyPhase.ScalingUp)
                    ? new ServiceState.ScaledDown(new CompletableFuture<>())
                    : new ServiceState.Stopped();
        }
        return new ServiceState.Running();
    }

    private static int resolveTargetReplicas(
            Integer fromStatus,
            Optional<DeploymentStateReader.DeploymentState> deployState) {
        if (fromStatus != null && fromStatus > 0) return fromStatus;
        return deployState.map(ds -> Math.max(ds.specReplicas(), 1)).orElse(1);
    }
}
