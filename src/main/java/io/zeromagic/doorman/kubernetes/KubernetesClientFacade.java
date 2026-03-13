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
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointBuilder;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.cli.KubernetesConfig;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.repository.crd.ScalingPolicyStatus;
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

    // ── EndpointRegistrar (Task-006) ──────────────────────────────────────────

    @Override
    public void register(String namespace, String serviceName) {
        registerEndpointSlice(namespace, serviceName);
        registerClassicEndpoints(namespace, serviceName);
    }

    private void registerEndpointSlice(String namespace, String serviceName) {
        try {
            String sliceName = "doorman-" + serviceName;

            // Derive ports from existing service slices; fall back to a single proxy port
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

            var existing = client.discovery().v1().endpointSlices()
                    .inNamespace(namespace).withName(sliceName).get();

            if (existing != null) {
                boolean alreadyPresent = existing.getEndpoints() != null
                        && existing.getEndpoints().stream()
                        .anyMatch(e -> e.getAddresses() != null
                                && e.getAddresses().contains(doormanConfig.podIp()));
                if (alreadyPresent) return;
                existing.getEndpoints().add(doormanEndpoint());
                client.discovery().v1().endpointSlices()
                        .inNamespace(namespace).resource(existing).update();
            } else {
                var slice = new EndpointSliceBuilder()
                        .withNewMetadata()
                            .withName(sliceName)
                            .withNamespace(namespace)
                            .addToLabels("kubernetes.io/service-name", serviceName)
                        .endMetadata()
                        .withAddressType("IPv4")
                        .withEndpoints(doormanEndpoint())
                        .withPorts(ports)
                        .build();
                client.discovery().v1().endpointSlices()
                        .inNamespace(namespace).resource(slice).create();
            }
        } catch (Exception e) {
            LOG.warn("Failed to register EndpointSlice for {}/{}: {}", namespace, serviceName, e.getMessage());
        }
    }

    private io.fabric8.kubernetes.api.model.discovery.v1.Endpoint doormanEndpoint() {
        return new EndpointBuilder()
                .withAddresses(doormanConfig.podIp())
                .withNewConditions()
                    .withReady(true).withServing(true).withTerminating(false)
                .endConditions()
                .build();
    }

    private void registerClassicEndpoints(String namespace, String serviceName) {
        try {
            var existing = client.endpoints().inNamespace(namespace).withName(serviceName).get();
            if (existing != null) {
                boolean alreadyPresent = existing.getSubsets() != null
                        && existing.getSubsets().stream()
                        .flatMap(s -> s.getAddresses() != null ? s.getAddresses().stream() : java.util.stream.Stream.empty())
                        .anyMatch(a -> doormanConfig.podIp().equals(a.getIp()));
                if (alreadyPresent) return;
                client.endpoints().inNamespace(namespace).withName(serviceName).edit(ep -> {
                    if (ep.getSubsets() == null) ep.setSubsets(new java.util.ArrayList<>());
                    ep.getSubsets().add(doormanSubset());
                    return ep;
                });
            } else {
                var ep = new EndpointsBuilder()
                        .withNewMetadata().withName(serviceName).withNamespace(namespace).endMetadata()
                        .withSubsets(doormanSubset())
                        .build();
                client.endpoints().inNamespace(namespace).resource(ep).create();
            }
        } catch (Exception e) {
            LOG.warn("Failed to register Endpoints for {}/{}: {}", namespace, serviceName, e.getMessage());
        }
    }

    private io.fabric8.kubernetes.api.model.EndpointSubset doormanSubset() {
        return new EndpointSubsetBuilder()
                .withAddresses(new EndpointAddressBuilder().withIp(doormanConfig.podIp()).build())
                .withPorts(new EndpointPortBuilder()
                        .withPort(doormanConfig.proxyPort()).withProtocol("TCP").build())
                .build();
    }

    @Override
    public void deregister(String namespace, String serviceName) {
        deregisterEndpointSlice(namespace, serviceName);
        deregisterClassicEndpoints(namespace, serviceName);
    }

    private void deregisterEndpointSlice(String namespace, String serviceName) {
        try {
            client.discovery().v1().endpointSlices()
                    .inNamespace(namespace).withName("doorman-" + serviceName).delete();
        } catch (Exception e) {
            LOG.warn("Failed to deregister EndpointSlice for {}/{}: {}", namespace, serviceName, e.getMessage());
        }
    }

    private void deregisterClassicEndpoints(String namespace, String serviceName) {
        try {
            var existing = client.endpoints().inNamespace(namespace).withName(serviceName).get();
            if (existing == null || existing.getSubsets() == null) return;
            boolean hadDoorman = existing.getSubsets().stream()
                    .flatMap(s -> s.getAddresses() != null ? s.getAddresses().stream() : java.util.stream.Stream.empty())
                    .anyMatch(a -> doormanConfig.podIp().equals(a.getIp()));
            if (!hadDoorman) return;
            client.endpoints().inNamespace(namespace).withName(serviceName).edit(ep -> {
                if (ep.getSubsets() != null) {
                    ep.getSubsets().removeIf(s -> s.getAddresses() != null
                            && s.getAddresses().stream()
                            .anyMatch(a -> doormanConfig.podIp().equals(a.getIp())));
                }
                return ep;
            });
        } catch (Exception e) {
            LOG.warn("Failed to deregister Endpoints for {}/{}: {}", namespace, serviceName, e.getMessage());
        }
    }

    // ── ServiceScaler ─────────────────────────────────────────────────────────

    @Override
    public void scaleUp(String namespace, String deploymentName, int targetReplicas) {
        LOG.info("[stub] scaleUp {}/{} to {}", namespace, deploymentName, targetReplicas);
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
            LOG.warn("Failed to scale down deployment {}/{}: {}", namespace, deploymentName, e.getMessage());
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PreDestroy
    void close() {
        client.close();
    }
}
