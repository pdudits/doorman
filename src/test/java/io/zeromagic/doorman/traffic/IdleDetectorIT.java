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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.k3s.K3sClusterExtension;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.scaling.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.scaling.ScaledApplicationRegistry;
import io.zeromagic.doorman.scaling.ServiceState;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.*;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link IdleDetector} against a real k3s cluster.
 *
 * <p>Uses {@link IdleDetectorTest.MutableClock} and calls {@link IdleDetector#evaluateAll}
 * directly (bypassing the scheduler) for deterministic, timing-independent verification.
 *
 * <p>Verifies that idle detection results in the {@link ScalingPolicy} status being patched
 * to {@link ScalingPolicyPhase#ScalingDown} in the Kubernetes API server.
 */
class IdleDetectorIT {

    private static final String NS      = "doorman-detector-it";
    private static final String SVC     = "detector-it-svc";
    private static final String DEPLOY  = "detector-it-dep";
    private static final String INGRESS = "detector-it-ingress";
    private static final int    PORT    = 8080;
    static final String LABEL = NS + "-" + SVC + "-" + PORT + "@kubernetes";
    private static final String POLICY  = "detector-it-policy";
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    @RegisterExtension
    static final K3sClusterExtension K3S = new K3sClusterExtension(NS);

    @Test
    void idleService_patchesScalingPolicyToScalingDown() {
        var ns     = K3S.namespace();
        var facade = K3S.facade();
        var client = K3S.client();
        var clock  = new IdleDetectorTest.MutableClock(Instant.EPOCH);

        // Create ingress in k3s
        client.network().v1().ingresses().inNamespace(ns).resource(ingress(ns)).createOrReplace();

        // Real status patcher + stub stubs for everything else
        var registry = new ScaledApplicationRegistry(
                facade,
                new ServiceScaler() {
                    @Override public void scaleUp(String n, String d, int r) {}
                    @Override public void scaleDown(String n, String d) {}
                },
                new EndpointRegistrar() {
                    @Override public void register(String n, String s) {}
                    @Override public void deregister(String n, String s) {}
                },
                (namespace, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(1, 1)),
                TIMEOUT
        );

        var resolver = new TraefikServiceNameResolver(facade);
        var config   = new TraefikConfig.Direct("http://fake", "5m", "15s");
        var detector = new IdleDetector(null, resolver, registry, config, clock);

        var policy = createScalingPolicy(ns);
        try {
            resolver.onPolicyAdded(policy);
            registry.onPolicyAdded(policy);

            assertThat(registry.byServiceName(ns, SVC).orElseThrow().currentState())
                    .isInstanceOf(ServiceState.Running.class);

            // Poll 1: establish baseline
            detector.evaluateAll(Map.of(LABEL, 0.0));

            // Poll 2: no traffic — idleSince = T0 (Instant.EPOCH)
            detector.evaluateAll(Map.of(LABEL, 0.0));

            // Advance time past the idle timeout
            clock.advance(TIMEOUT.plusSeconds(1));

            // Poll 3: idle duration >= timeout → beginScalingDown → k3s status patched
            detector.evaluateAll(Map.of(LABEL, 0.0));

            assertThat(registry.byServiceName(ns, SVC).orElseThrow().currentState())
                    .as("in-memory state should be ScalingDown")
                    .isInstanceOf(ServiceState.ScalingDown.class);

            var updated = client.resources(ScalingPolicy.class)
                    .inNamespace(ns).withName(POLICY).get();
            assertThat(updated).isNotNull();
            assertThat(updated.getStatus()).isNotNull();
            assertThat(updated.getStatus().getPhase())
                    .as("ScalingPolicy status in k3s must be ScalingDown")
                    .isEqualTo(ScalingPolicyPhase.ScalingDown);

        } finally {
            try { client.resources(ScalingPolicy.class).inNamespace(ns).withName(POLICY).delete(); }
            catch (Exception ignored) {}
            try { client.network().v1().ingresses().inNamespace(ns).withName(INGRESS).delete(); }
            catch (Exception ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScalingPolicy createScalingPolicy(String ns) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SVC);
        spec.setDeploymentName(DEPLOY);
        spec.setIngressName(INGRESS);

        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(POLICY).build());
        policy.setSpec(spec);

        return K3S.client().resources(ScalingPolicy.class).inNamespace(ns).resource(policy).create();
    }

    private io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress(String ns) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(ns).withName(INGRESS).endMetadata()
                .withNewSpec()
                    .addNewRule().withNewHttp()
                        .addNewPath().withPathType("Prefix").withPath("/")
                            .withNewBackend().withNewService()
                                .withName(SVC).withNewPort().withNumber(PORT).endPort()
                            .endService().endBackend()
                        .endPath()
                    .endHttp().endRule()
                .endSpec()
                .build();
    }
}
