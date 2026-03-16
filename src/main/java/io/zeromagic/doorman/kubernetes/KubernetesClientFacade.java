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
package io.zeromagic.doorman.kubernetes;

import io.avaje.inject.PreDestroy;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.dsl.NonDeletingOperation;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.cli.KubernetesConfig;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import io.zeromagic.doorman.scaling.EndpointRegistrar;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Single boundary to the Kubernetes API. Implements the full {@link KubernetesFacade}:
 * real implementations for {@link DeploymentStateReader}, {@link ScalingPolicyStatusPatcher},
 * and {@link #inform}; stub implementations for {@link EndpointRegistrar} and
 * {@link ServiceScaler} until tasks 006 and 008 are completed.
 *
 * <p>Absorbs {@code ClientProvider}: builds the {@link KubernetesClient} from
 * optional kubeContext config and closes it on shutdown.
 */
@Singleton
class KubernetesClientFacade implements KubernetesFacade {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesClientFacade.class);

    private final KubernetesClient client;
    private final DoormanConfig doormanConfig;

    @Inject
    KubernetesClientFacade(Optional<KubernetesConfig> config, DoormanConfig doormanConfig) {
        this.doormanConfig = doormanConfig;
        this.client = config
                .map(c -> new KubernetesClientBuilder()
                        .withConfig(switch (c) {
                            case KubernetesConfig.Context ctx -> Config.autoConfigure(ctx.kubeContext());
                            case KubernetesConfig.Raw raw -> Config.fromKubeconfig(raw.kubeConfigYaml());
                        })
                        .build())
                .orElseGet(() -> new KubernetesClientBuilder().build());
    }

    // ── inform ────────────────────────────────────────────────────────────────

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public <T extends HasMetadata> AutoCloseable inform(
            Class<T> type, String namespace, String labelSelector, ResourceEventHandler<T> handler) {
        var operation = client.resources(type);
        var scoped = namespace != null ? operation.inNamespace(namespace) : operation;
        var filtered = labelSelector != null ? scoped.withLabelSelector(labelSelector) : scoped;
        return filtered.inform(handler);
    }

    // ── DeploymentStateReader ─────────────────────────────────────────────────

    @Override
    public Optional<DeploymentState> read(String namespace, String deploymentName) {
        var deployment = client.apps().deployments()
                .inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null) return Optional.empty();
        int spec = deployment.getSpec() != null && deployment.getSpec().getReplicas() != null
                ? deployment.getSpec().getReplicas() : 0;
        int ready = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas() : 0;
        return Optional.of(new DeploymentState(spec, ready));
    }

    // ── ScalingPolicyStatusPatcher ────────────────────────────────────────────

    @Override
    public void patch(String namespace, String name, ScalingPolicyPhase phase,
                      Integer targetReplicas, String message) {
        try {
            var policy = new ScalingPolicy();
            policy.setMetadata(new ObjectMetaBuilder()
                    .withNamespace(namespace).withName(name).build());
            var status = new ScalingPolicyStatus();
            status.setPhase(phase);
            status.setLastTransitionTime(OffsetDateTime.now());
            status.setTargetReplicas(targetReplicas);
            status.setMessage(message);
            policy.setStatus(status);
            client.resource(policy).updateStatus();
        } catch (Exception e) {
            LOG.warn("Failed to patch status for ScalingPolicy {}/{}: {}", namespace, name, e.getMessage());
        }
    }

    // ── Ingress lookup ────────────────────────────────────────────────────────

    @Override
    public Optional<Ingress> getIngress(String namespace, String name) {
        return Optional.ofNullable(
                client.network().v1().ingresses().inNamespace(namespace).withName(name).get());
    }

    @Override
    public void addEndpointSubset(Endpoints newObj, String ip, int port) {
        retryOnConflict(3, () -> client.resource(newObj).edit(EndpointsBuilder.class, (e) -> _addEndpointSubset(e, ip, port)));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resulting reinstiated endpoint: {}", client.resource(newObj).get());
        }
    }

    @Override
    public void addEndpointSubset(String namespace, String serviceName, String ip, int port) {
        retryOnConflict(3,
                () -> client.endpoints().inNamespace(namespace).withName(serviceName).edit(EndpointsBuilder.class, (e) -> _addEndpointSubset(e, ip, port)));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resulting endpoint: {}", client.endpoints().inNamespace(namespace).withName(serviceName).get());
        }
    }

    private Endpoints _addEndpointSubset(EndpointsBuilder endpoint, String ip, int port) {
        return endpoint.addNewSubset()
                .addNewAddress().withIp(ip).endAddress()
                .addNewPort(null, null, port, "TCP")
                .endSubset()
                .build();
    }

    private void retryOnConflict(int limit, Runnable op) {
        KubernetesClientException ex = null;
        for (int i = 0; i < limit; i++) {
            try {
                op.run();
                return;
            } catch (KubernetesClientException e) {
                if (e.getCode() == 409) {
                    ex = e;
                } else {
                    throw e;
                }
            }
        }
        if (ex != null)
            throw ex;
    }

    @Override
    public void removeEndpointSubset(String namespace, String serviceName, String ip, int port) {
        retryOnConflict(3, () -> client.endpoints().inNamespace(namespace).withName(serviceName).edit(EndpointsBuilder.class, (e) -> {
            e.removeMatchingFromSubsets(s ->
                    s.hasMatchingAddress(a -> a.getIp().equals(ip)));
        }));
    }

    @Override
    public void addEndpointSlice(String namespace, String serviceName, String ip, int port) {
        var sliceName = "doorman-"+serviceName;
        var existingSlices = client.discovery().v1().endpointSlices()
                .inNamespace(namespace)
                .withLabel("kubernetes.io/service-name", serviceName)
                .list().getItems();

        var ports = existingSlices.stream()
                .filter(s -> !sliceName.equals(s.getMetadata().getName()))
                .flatMap(s -> s.getPorts() != null ? s.getPorts().stream() : java.util.stream.Stream.empty())
                .map(p -> new io.fabric8.kubernetes.api.model.discovery.v1.EndpointPortBuilder()
                        .withName(p.getName())
                        .withPort(doormanConfig.proxyPort())
                        .withProtocol(p.getProtocol() != null ? p.getProtocol() : "TCP")
                        .build())
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        if (ports.isEmpty()) {
            ports = List.of(new io.fabric8.kubernetes.api.model.discovery.v1.EndpointPortBuilder()
                    .withName("http")
                    .withPort(doormanConfig.proxyPort())
                    .withProtocol("TCP")
                    .build());
        }

        var slice = new EndpointSliceBuilder()
                .withNewMetadata()
                .withName(sliceName)
                .withNamespace(namespace)
                .addToLabels("kubernetes.io/service-name", serviceName)
                .endMetadata()
                .withAddressType("IPv4")
                .addNewEndpoint()
                .withAddresses(ip)
                .withNewConditions()
                .withReady(true).withServing(true).withTerminating(false)
                .endConditions()
                .endEndpoint()
                .withPorts(ports)
                .build();
        retryOnConflict(3, () -> client.resource(slice).unlock().createOr(NonDeletingOperation::update));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Resulting slices: {}", client.discovery()
                    .v1().endpointSlices()
                    .inNamespace(namespace).withLabel("kubernetes.io/service-name", serviceName).list());
        }
    }

    @Override
    public void addEndpointSlice(EndpointSlice obj) {
        retryOnConflict(3, () -> client.resource(obj).unlock().createOr(NonDeletingOperation::update));
    }

    @Override
    public void removeEndpointSlice(String namespace, String serviceName) {
        client.discovery().v1().endpointSlices().inNamespace(namespace).withName("doorman-"+serviceName).delete();
    }

    public void deregister(String namespace, String serviceName) {
        deregisterEndpointSlice(namespace, serviceName);
        deregisterClassicEndpoints(namespace, serviceName);
    }

    private void deregisterEndpointSlice(String namespace, String serviceName) {
        try {
            client.discovery().v1().endpointSlices()
                    .inNamespace(namespace).withName("doorman-" + serviceName).delete();
        } catch (Exception e) {
            LOG.warn("Failed to deregister EndpointSlice for {}/{}", namespace, serviceName, e);
        }
    }

    private void deregisterClassicEndpoints(String namespace, String serviceName) {
        try {
            // Release Doorman's server-side apply ownership of subsets so the K8s endpoint
            // controller can repopulate subsets with the real pod addresses on scale-up.
            var ep = new EndpointsBuilder()
                    .withNewMetadata().withName(serviceName).withNamespace(namespace).endMetadata()
                    .withSubsets()  // explicitly empty — releases our field manager's claim
                    .build();
            client.endpoints().inNamespace(namespace).resource(ep)
                    .fieldManager("doorman")
                    .serverSideApply();
        } catch (Exception e) {
            LOG.warn("Failed to deregister Endpoints for {}/{}", namespace, serviceName, e);
        }
    }

// ── ServiceScaler ─────────────────────────────────────────────────────────

    @Override
    public void scaleUp(String namespace, String deploymentName, int targetReplicas) {
        LOG.info("Scaling up deployment {}/{} to {}", namespace, deploymentName, targetReplicas);
        try {
            client.apps().deployments()
                    .inNamespace(namespace).withName(deploymentName)
                    .edit(d -> {
                        d.getSpec().setReplicas(targetReplicas);
                        return d;
                    });
        } catch (Exception e) {
            LOG.warn("Failed to scale up deployment {}/{}", namespace, deploymentName);
        }
    }

    @Override
    public void scaleDown(String namespace, String deploymentName) {
        LOG.info("Scaling down deployment {}/{} to 0", namespace, deploymentName);
        try {
            client.apps().deployments()
                    .inNamespace(namespace).withName(deploymentName)
                    .edit(d -> {
                        d.getSpec().setReplicas(0);
                        return d;
                    });
        } catch (Exception e) {
            LOG.warn("Failed to scale down deployment {}/{}", namespace, deploymentName, e);
        }
    }

// ── Lifecycle ─────────────────────────────────────────────────────────────

    @PreDestroy
    void close() {
        client.close();
    }
}
