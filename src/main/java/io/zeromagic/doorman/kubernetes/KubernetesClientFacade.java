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
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.KubernetesConfig;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.repository.crd.ScalingPolicyStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
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

    KubernetesClientFacade(Optional<KubernetesConfig> config) {
        this.client = config
                .map(c -> new KubernetesClientBuilder()
                        .withConfig(Config.autoConfigure(c.kubeContext()))
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

    // ── EndpointRegistrar (stub — Task-006) ───────────────────────────────────

    @Override
    public void register(String namespace, String serviceName) {
        LOG.info("[stub] register endpoint {}/{}", namespace, serviceName);
    }

    @Override
    public void deregister(String namespace, String serviceName) {
        LOG.info("[stub] deregister endpoint {}/{}", namespace, serviceName);
    }

    // ── ServiceScaler (stub — Task-008) ───────────────────────────────────────

    @Override
    public void scaleUp(String namespace, String deploymentName, int targetReplicas) {
        LOG.info("[stub] scaleUp {}/{} to {}", namespace, deploymentName, targetReplicas);
    }

    @Override
    public void scaleDown(String namespace, String deploymentName) {
        LOG.info("[stub] scaleDown {}/{}", namespace, deploymentName);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @PreDestroy
    void close() {
        client.close();
    }
}
