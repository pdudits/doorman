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

package io.zeromagic.doorman.scaling;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.zeromagic.doorman.k3s.K3sClusterExtension;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test verifying that {@code beginScalingDown} causes the real Kubernetes Deployment
 * to be patched to 0 replicas via {@link io.zeromagic.doorman.kubernetes.KubernetesClientFacade}.
 */
class DeploymentScalerIT {

    private static final String SERVICE = "scaler-it-svc";
    private static final String DEPLOY  = "scaler-it-dep";
    private static final String POLICY  = "scaler-it-policy";

    @RegisterExtension
    static final K3sClusterExtension K3S = new K3sClusterExtension("doorman-scaler-it");

    @Test
    void beginScalingDown_patchesDeploymentReplicasToZero() {
        var ns     = K3S.namespace();
        var facade = K3S.facade();

        // Create a real deployment with 2 replicas
        var deployment = new DeploymentBuilder()
                .withMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(DEPLOY).build())
                .withNewSpec()
                    .withReplicas(2)
                    .withNewSelector().addToMatchLabels("app", DEPLOY).endSelector()
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("app", DEPLOY).endMetadata()
                        .withNewSpec()
                            .addNewContainer()
                                .withName("nginx").withImage("nginx:alpine")
                            .endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        K3S.client().apps().deployments().inNamespace(ns).resource(deployment).create();

        try {
            // DeploymentStateReader: report spec=2, ready=2 so registry starts in Running state
            DeploymentStateReader reader = (namespace, dep) ->
                    Optional.of(new DeploymentStateReader.DeploymentState(2, 2));

            var policy = createScalingPolicy(ns);
            var registry = new ScaledApplicationRegistry(
                    facade,                              // ScalingPolicyStatusPatcher (real)
                    facade,                              // ServiceScaler (real — will call scaleDown)
                    new EndpointRegistrar() {
                        @Override public void register(String n, String s) {}
                        @Override public void deregister(String n, String s) {}
                    },
                    reader,
                    Duration.ofMinutes(5)
            );

            registry.onAdded(policy);
            assertThat(registry.byServiceName(ns, SERVICE).orElseThrow().currentState())
                    .as("should start as Running")
                    .isInstanceOf(ServiceState.Running.class);

            registry.beginScalingDown(ns, SERVICE);

            // Poll the deployment spec from k3s
            var updated = K3S.client().apps().deployments().inNamespace(ns).withName(DEPLOY).get();
            assertThat(updated).isNotNull();
            assertThat(updated.getSpec().getReplicas())
                    .as("deployment.spec.replicas must be 0 after beginScalingDown")
                    .isEqualTo(0);

        } finally {
            try { K3S.client().apps().deployments().inNamespace(ns).withName(DEPLOY).delete(); } catch (Exception ignored) {}
            try { K3S.client().resources(ScalingPolicy.class).inNamespace(ns).withName(POLICY).delete(); } catch (Exception ignored) {}
        }
    }

    private ScalingPolicy createScalingPolicy(String ns) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SERVICE);
        spec.setDeploymentName(DEPLOY);
        spec.setIngressName("scaler-it-ingress");

        var status = new ScalingPolicyStatus();
        status.setPhase(ScalingPolicyPhase.Running);
        status.setTargetReplicas(2);

        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(POLICY).build());
        policy.setSpec(spec);
        policy.setStatus(status);

        return K3S.client().resources(ScalingPolicy.class).inNamespace(ns).resource(policy).create();
    }
}
