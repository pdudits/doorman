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
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.k3s.K3sClusterExtension;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
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
 * Integration test verifying endpoint registration and deregistration against a real k3s cluster.
 * Covers:
 *  - register() adds Doorman IP to EndpointSlice and classic Endpoints
 *  - register() is idempotent
 *  - deregister() removes the Doorman EndpointSlice
 *  - ScaledApplicationRegistry calls register() after transitioning to ScaledDown
 */
class EndpointRegistrarIT {

    private static final String SERVICE    = "registrar-it-svc";
    private static final String DEPLOY     = "registrar-it-dep";
    private static final String POLICY     = "registrar-it-policy";
    private static final String DOORMAN_IP = "10.42.99.99";
    private static final int    PROXY_PORT = 8080;

    private static final DoormanConfig DOORMAN_CONFIG = new DoormanConfig(DOORMAN_IP, PROXY_PORT, Duration.ofSeconds(60));

    @RegisterExtension
    static final K3sClusterExtension K3S = new K3sClusterExtension("doorman-registrar-it");

    @Test
    void register_addsEndpointSliceAndClassicEndpoints() {
        var ns     = K3S.namespace();
        var facade = K3S.facade(DOORMAN_CONFIG);
        createService(ns);

        try {
            facade.register(ns, SERVICE);

            // Doorman EndpointSlice must exist and contain Doorman IP
            var slices = K3S.client().discovery().v1().endpointSlices()
                    .inNamespace(ns).withLabel("kubernetes.io/service-name", SERVICE).list().getItems();
            assertThat(slices).as("at least one EndpointSlice should exist").isNotEmpty();
            boolean doormanInSlice = slices.stream()
                    .filter(s -> s.getEndpoints() != null)
                    .flatMap(s -> s.getEndpoints().stream())
                    .flatMap(e -> e.getAddresses() != null ? e.getAddresses().stream() : java.util.stream.Stream.empty())
                    .anyMatch(DOORMAN_IP::equals);
            assertThat(doormanInSlice).as("Doorman IP must be in EndpointSlice").isTrue();

            // Classic Endpoints must contain Doorman IP
            var ep = K3S.client().endpoints().inNamespace(ns).withName(SERVICE).get();
            assertThat(ep).as("classic Endpoints must exist").isNotNull();
            boolean doormanInEp = ep.getSubsets().stream()
                    .flatMap(s -> s.getAddresses().stream())
                    .anyMatch(a -> DOORMAN_IP.equals(a.getIp()));
            assertThat(doormanInEp).as("Doorman IP must be in classic Endpoints").isTrue();

            // Idempotency: second register() must not duplicate entries
            facade.register(ns, SERVICE);
            var slicesAfter = K3S.client().discovery().v1().endpointSlices()
                    .inNamespace(ns).withLabel("kubernetes.io/service-name", SERVICE).list().getItems();
            long count = slicesAfter.stream()
                    .filter(s -> s.getEndpoints() != null)
                    .flatMap(s -> s.getEndpoints().stream())
                    .flatMap(e -> e.getAddresses() != null ? e.getAddresses().stream() : java.util.stream.Stream.empty())
                    .filter(DOORMAN_IP::equals).count();
            assertThat(count).as("idempotent register must not duplicate Doorman IP").isEqualTo(1L);

        } finally {
            safeDelete(ns, facade);
        }
    }

    @Test
    void deregister_removesEndpointSlice() {
        var ns     = K3S.namespace();
        var facade = K3S.facade(DOORMAN_CONFIG);
        createService(ns);

        try {
            facade.register(ns, SERVICE);
            facade.deregister(ns, SERVICE);

            var doormanSlice = K3S.client().discovery().v1().endpointSlices()
                    .inNamespace(ns).withName("doorman-" + SERVICE).get();
            assertThat(doormanSlice).as("doorman EndpointSlice must be deleted after deregister").isNull();

            var ep = K3S.client().endpoints().inNamespace(ns).withName(SERVICE).get();
            if (ep != null && ep.getSubsets() != null) {
                boolean stillPresent = ep.getSubsets().stream()
                        .flatMap(s -> s.getAddresses() != null
                                ? s.getAddresses().stream() : java.util.stream.Stream.empty())
                        .anyMatch(a -> DOORMAN_IP.equals(a.getIp()));
                assertThat(stillPresent).as("Doorman IP must not remain in Endpoints after deregister").isFalse();
            }
        } finally {
            safeDelete(ns, facade);
        }
    }

    @Test
    void registry_callsRegisterAfterScaledDownTransition() {
        var ns     = K3S.namespace();
        var facade = K3S.facade(DOORMAN_CONFIG);
        createService(ns);
        createDeployment(ns);
        var policy = createScalingPolicy(ns);

        try {
            DeploymentStateReader reader =
                    (namespace, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(2, 2));

            var registry = new ScaledApplicationRegistry(
                    facade,   // ScalingPolicyStatusPatcher
                    facade,   // ServiceScaler
                    facade,   // EndpointRegistrar (real — will register Doorman IP)
                    reader,
                    Duration.ofMinutes(5));

            registry.onAdded(policy);
            assertThat(registry.byServiceName(ns, SERVICE).orElseThrow().currentState())
                    .isInstanceOf(ServiceState.Running.class);

            // ScalingDown → confirmScaledDown via endpoint-drain event
            registry.byServiceName(ns, SERVICE).orElseThrow().beginScalingDown();
            registry.onRealEndpointsDrained(ns, SERVICE);

            assertThat(registry.byServiceName(ns, SERVICE).orElseThrow().currentState())
                    .as("state must be ScaledDown after endpoint drain")
                    .isInstanceOf(ServiceState.ScaledDown.class);

            // ScalingPolicy status patched to ScaledDown
            var updated = K3S.client().resources(ScalingPolicy.class)
                    .inNamespace(ns).withName(POLICY).get();
            assertThat(updated.getStatus().getPhase())
                    .isEqualTo(ScalingPolicyPhase.ScaledDown);

            // Doorman IP must be registered in EndpointSlice
            var slices = K3S.client().discovery().v1().endpointSlices()
                    .inNamespace(ns).withLabel("kubernetes.io/service-name", SERVICE).list().getItems();
            boolean doormanInSlice = slices.stream()
                    .filter(s -> s.getEndpoints() != null)
                    .flatMap(s -> s.getEndpoints().stream())
                    .flatMap(e -> e.getAddresses() != null ? e.getAddresses().stream() : java.util.stream.Stream.empty())
                    .anyMatch(DOORMAN_IP::equals);
            assertThat(doormanInSlice).as("Doorman IP must be in EndpointSlice after ScaledDown").isTrue();

        } finally {
            safeDelete(ns, facade);
            try { K3S.client().resources(ScalingPolicy.class).inNamespace(ns).withName(POLICY).delete(); }
            catch (Exception ignored) {}
            try { K3S.client().apps().deployments().inNamespace(ns).withName(DEPLOY).delete(); }
            catch (Exception ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createService(String ns) {
        var svc = new ServiceBuilder()
                .withNewMetadata().withName(SERVICE).withNamespace(ns).endMetadata()
                .withNewSpec()
                    .addToSelector("app", DEPLOY)
                    .addNewPort().withPort(80).withName("http").endPort()
                .endSpec()
                .build();
        K3S.client().services().inNamespace(ns).resource(svc).createOrReplace();
    }

    private void createDeployment(String ns) {
        var dep = new DeploymentBuilder()
                .withMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(DEPLOY).build())
                .withNewSpec()
                    .withReplicas(2)
                    .withNewSelector().addToMatchLabels("app", DEPLOY).endSelector()
                    .withNewTemplate()
                        .withNewMetadata().addToLabels("app", DEPLOY).endMetadata()
                        .withNewSpec()
                            .addNewContainer().withName("nginx").withImage("nginx:alpine").endContainer()
                        .endSpec()
                    .endTemplate()
                .endSpec()
                .build();
        K3S.client().apps().deployments().inNamespace(ns).resource(dep).createOrReplace();
    }

    private ScalingPolicy createScalingPolicy(String ns) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SERVICE);
        spec.setDeploymentName(DEPLOY);
        spec.setIngressName("registrar-it-ingress");
        var status = new ScalingPolicyStatus();
        status.setPhase(ScalingPolicyPhase.Running);
        status.setTargetReplicas(2);
        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(POLICY).build());
        policy.setSpec(spec);
        policy.setStatus(status);
        return K3S.client().resources(ScalingPolicy.class).inNamespace(ns).resource(policy).create();
    }

    private void safeDelete(String ns, io.zeromagic.doorman.kubernetes.KubernetesFacade facade) {
        try { facade.deregister(ns, SERVICE); } catch (Exception ignored) {}
        try { K3S.client().services().inNamespace(ns).withName(SERVICE).delete(); } catch (Exception ignored) {}
    }
}
