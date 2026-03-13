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
import io.zeromagic.doorman.k3s.K3sClusterExtension;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link ScaledApplicationRegistry#beginScalingDown} against a real
 * k3s cluster. Verifies that calling {@code beginScalingDown} results in the
 * {@link ScalingPolicy} status being patched to {@link ScalingPolicyPhase#ScalingDown}
 * in the Kubernetes API server.
 */
class ScaledApplicationRegistryIT {

    private static final String SERVICE   = "registry-it-svc";
    private static final String DEPLOY    = "registry-it-dep";
    private static final String POLICY    = "registry-it-policy";

    @RegisterExtension
    static final K3sClusterExtension K3S = new K3sClusterExtension("doorman-registry-it");

    @Test
    void beginScalingDown_patchesScalingPolicyStatusInCluster() {
        var ns     = K3S.namespace();
        var facade = K3S.facade();

        // Stub: pretend deployment is already running (1/1 ready).
        // We only care about status patching here, not actual scale operations.
        DeploymentStateReader stubReader =
                (namespace, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(1, 1));

        var registry = new ScaledApplicationRegistry(
                facade,                              // ScalingPolicyStatusPatcher (real)
                new ServiceScaler() {
                    @Override public void scaleUp(String n, String d, int r) {}
                    @Override public void scaleDown(String n, String d) {}
                },
                new EndpointRegistrar() {
                    @Override public void register(String n, String s) {}
                    @Override public void deregister(String n, String s) {}
                },
                stubReader,
                Duration.ofMinutes(5)
        );

        var policy = createScalingPolicy(ns);
        try {
            registry.onAdded(policy);

            assertThat(registry.byServiceName(ns, SERVICE).orElseThrow().currentState())
                    .as("initial state should be Running (stub returns 1 ready replica)")
                    .isInstanceOf(ServiceState.Running.class);

            registry.beginScalingDown(ns, SERVICE);

            // Read the ScalingPolicy back from k3s and verify the patched phase
            var updated = K3S.client().resources(ScalingPolicy.class)
                    .inNamespace(ns).withName(POLICY).get();
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isNotNull();
            assertThat(updated.getStatus().getPhase())
                    .as("ScalingPolicy status phase must be ScalingDown after beginScalingDown")
                    .isEqualTo(ScalingPolicyPhase.ScalingDown);

        } finally {
            try { K3S.client().resources(ScalingPolicy.class).inNamespace(ns).withName(POLICY).delete(); }
            catch (Exception ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScalingPolicy createScalingPolicy(String ns) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SERVICE);
        spec.setDeploymentName(DEPLOY);
        spec.setIngressName("registry-it-ingress");

        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(POLICY).build());
        policy.setSpec(spec);

        return K3S.client().resources(ScalingPolicy.class).inNamespace(ns).resource(policy).create();
    }
}
