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
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ScaledApplicationRegistryTest {

    record PatchCall(String namespace, String name, ScalingPolicyPhase phase) {}
    record ScaleUpCall(String namespace, String deployment, int replicas) {}
    record ScaleDownCall(String namespace, String deployment) {}
    record RegisterCall(String namespace, String service) {}

    List<PatchCall> patches;
    List<ScaleUpCall> scaleUpCalls;
    List<ScaleDownCall> scaleDownCalls;
    List<RegisterCall> registerCalls;
    List<RegisterCall> deregisterCalls;

    ScaledApplicationRegistry registry;

    @BeforeEach
    void setUp() {
        patches = new ArrayList<>();
        scaleUpCalls = new ArrayList<>();
        scaleDownCalls = new ArrayList<>();
        registerCalls = new ArrayList<>();
        deregisterCalls = new ArrayList<>();
        registry = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(3, 3)));
    }

    private ScaledApplicationRegistry registryWith(DeploymentStateReader reader) {
        return new ScaledApplicationRegistry(
                (ns, name, phase, target, msg) -> patches.add(new PatchCall(ns, name, phase)),
                new ServiceScaler() {
                    @Override public void scaleUp(String ns, String dep, int reps) { scaleUpCalls.add(new ScaleUpCall(ns, dep, reps)); }
                    @Override public void scaleDown(String ns, String dep) { scaleDownCalls.add(new ScaleDownCall(ns, dep)); }
                },
                new EndpointRegistrar() {
                    @Override public void register(String ns, String svc) { registerCalls.add(new RegisterCall(ns, svc)); }
                    @Override public void deregister(String ns, String svc) { deregisterCalls.add(new RegisterCall(ns, svc)); }
                },
                reader,
                Duration.ofMinutes(5)
        );
    }

    private static ScalingPolicy policy(String ns, String name, String svc, String dep) {
        return policy(ns, name, svc, dep, null, null);
    }

    private static ScalingPolicy policy(String ns, String name, String svc, String dep,
                                        ScalingPolicyPhase phase, Integer targetReplicas) {
        var p = new ScalingPolicy();
        p.setMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(name).build());
        var spec = new ScalingPolicySpec();
        spec.setServiceName(svc);
        spec.setDeploymentName(dep);
        p.setSpec(spec);
        if (phase != null) {
            var status = new ScalingPolicyStatus();
            status.setPhase(phase);
            status.setTargetReplicas(targetReplicas);
            p.setStatus(status);
        }
        return p;
    }

    private static Deployment deployment(String ns, String name, int spec, int ready) {
        return new DeploymentBuilder()
                .withMetadata(new ObjectMetaBuilder().withNamespace(ns).withName(name).build())
                .withNewSpec().withReplicas(spec).endSpec()
                .withNewStatus().withReplicas(spec).withReadyReplicas(ready).endStatus()
                .build();
    }

    // -------------------------------------------------------------------------
    // onAdded — initial state detection
    // -------------------------------------------------------------------------

    @Test
    void onPolicyAdded_initialPhaseRunning_deploymentHasReadyReplicas() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        var app = registry.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.Running.class);
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.Running);
    }

    @Test
    void onPolicyAdded_initialPhaseStopped_deploymentHasZeroReplicas() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        var app = reg.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.Stopped.class);
    }

    @Test
    void onPolicyAdded_initialPhaseStopped_deploymentNotFound() {
        var reg = registryWith((ns, dep) -> Optional.empty());
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        var app = reg.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.Stopped.class);
    }

    @Test
    void onPolicyAdded_initialPhaseScaledDown_statusSaysScaledDown() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        var app = reg.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.ScaledDown.class);
    }

    @Test
    void onPolicyAdded_registersAllIndexes() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        assertThat(registry.byServiceName("ns", "svc")).isPresent();
        assertThat(registry.all()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // onDeleted
    // -------------------------------------------------------------------------

    @Test
    void onPolicyDeleted_removesFromAllIndexes() {
        var p = policy("ns", "pol", "svc", "dep");
        registry.onPolicyAdded(p);
        registry.onPolicyDeleted(p);
        assertThat(registry.byServiceName("ns", "svc")).isEmpty();
        assertThat(registry.all()).isEmpty();
    }

    @Test
    void onPolicyDeleted_cancelsPendingFuture() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        var p = policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3);
        reg.onPolicyAdded(p);
        var app = reg.byServiceName("ns", "svc").orElseThrow();
        var future = ((ServiceState.ScaledDown) app.currentState()).ready();
        reg.onPolicyDeleted(p);
        assertThat(future).isCancelled();
    }

    // -------------------------------------------------------------------------
    // DeploymentEvents
    // -------------------------------------------------------------------------

    @Test
    void onDeploymentChanged_ignoredForUnknownDeployment() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        patches.clear();
        registry.onDeploymentChanged(deployment("ns", "OTHER", 0, 0));
        assertThat(patches).as("unknown deployment must produce no side effects").isEmpty();
    }

    @Test
    void onDeploymentChanged_scalingDownToScaledDown_whenReadyReplicasZero() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow()
                .beginScalingDown();
        patches.clear();

        registry.onDeploymentChanged(deployment("ns", "dep", 0, 0));

        var app = registry.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.ScaledDown.class);
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.ScaledDown);
    }

    @Test
    void onDeploymentChanged_scalingUpToRunning_whenAtLeastOneReady() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        var p = policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3);
        reg.onPolicyAdded(p);
        reg.awaitReady("ns", "svc"); // transitions to ScalingUp
        patches.clear();

        // Only 1 of 3 replicas is ready — still enough to redirect traffic
        reg.onDeploymentChanged(deployment("ns", "dep", 3, 1));

        var app = reg.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.Running.class);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.Running);
    }

    @Test
    void onDeploymentChanged_readyReplicasZero_butNotScalingDown_noTransition() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep")); // Running
        patches.clear();
        registry.onDeploymentChanged(deployment("ns", "dep", 0, 0));
        // State was Running, not ScalingDown — should stay Running
        assertThat(registry.byServiceName("ns", "svc").orElseThrow().currentState())
                .isInstanceOf(ServiceState.Running.class);
        assertThat(patches).isEmpty();
    }

    // -------------------------------------------------------------------------
    // EndpointsEvents — real endpoints
    // -------------------------------------------------------------------------

    @Test
    void onRealEndpointsDrained_scalingDownToScaledDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow()
                .beginScalingDown();
        patches.clear();

        registry.onRealEndpointsDrained("ns", "svc");

        assertThat(registry.byServiceName("ns", "svc").orElseThrow().currentState())
                .isInstanceOf(ServiceState.ScaledDown.class);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.ScaledDown);
    }

    @Test
    void onRealEndpointsDrained_ignoredForUnknownService() {
        // must not throw
        registry.onRealEndpointsDrained("ns", "unknown-svc");
    }

    // -------------------------------------------------------------------------
    // EndpointsEvents — fight-back
    // -------------------------------------------------------------------------

    @Test
    void onDoormanEndpointRemoved_fightBack_whenScaledDown() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        registerCalls.clear(); // onAdded already called register() for initial ScaledDown state
        reg.onDoormanEndpointRemoved("ns", "svc");
        assertThat(registerCalls).hasSize(1);
        assertThat(registerCalls.get(0).service()).isEqualTo("svc");
    }

    @Test
    void onDoormanEndpointRemoved_fightBack_whenScalingUp() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        reg.awaitReady("ns", "svc"); // → ScalingUp
        registerCalls.clear();
        reg.onDoormanEndpointRemoved("ns", "svc");
        assertThat(registerCalls).hasSize(1);
    }

    @Test
    void onDoormanEndpointRemoved_noFightBack_whenRunning() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.onDoormanEndpointRemoved("ns", "svc");
        assertThat(registerCalls).as("must not fight back when Running").isEmpty();
    }

    // -------------------------------------------------------------------------
    // awaitReady() — proxy entry point
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_scaledDown_triggersScaleUp() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        var future = reg.awaitReady("ns", "svc");
        assertThat(future).isNotDone();
        assertThat(scaleUpCalls).hasSize(1);
        assertThat(scaleUpCalls.get(0).deployment()).isEqualTo("dep");
        assertThat(scaleUpCalls.get(0).replicas()).isEqualTo(3);
    }

    @Test
    void awaitReady_scalingUp_noDoubleScaleUp() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        reg.awaitReady("ns", "svc"); // first call → ScalingUp + scaleUp()
        reg.awaitReady("ns", "svc"); // second call → same future, no scaleUp()
        assertThat(scaleUpCalls).as("scaleUp must be called exactly once").hasSize(1);
    }

    @Test
    void awaitReady_concurrentCalls_singleScaleUpCall() throws Exception {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 2));

        int threads = 20;
        var executor = Executors.newFixedThreadPool(threads);
        var latch = new CountDownLatch(1);
        List<Future<CompletableFuture<Void>>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return reg.awaitReady("ns", "svc");
            }));
        }
        latch.countDown();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        for (var f : futures) {
            assertThat(f.get()).isNotNull();
        }
        assertThat(scaleUpCalls).as("concurrent awaitReady must produce exactly one scaleUp").hasSize(1);
    }

    @Test
    void awaitReady_unknownService_returnsFailedFuture() {
        var future = registry.awaitReady("ns", "not-managed");
        assertThat(future).isCompletedExceptionally();
    }

    // -------------------------------------------------------------------------
    // EndpointSliceEvents — renamed methods
    // -------------------------------------------------------------------------

    @Test
    void onRealSlicesDrained_scalingDownToScaledDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow()
                .beginScalingDown();
        patches.clear();

        registry.onRealSlicesDrained("ns", "svc");

        assertThat(registry.byServiceName("ns", "svc").orElseThrow().currentState())
                .isInstanceOf(ServiceState.ScaledDown.class);
    }

    @Test
    void onDoormanSliceRemoved_fightBack_whenScaledDown() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        registerCalls.clear(); // onAdded already called register() for initial ScaledDown state
        reg.onDoormanSliceRemoved("ns", "svc");
        assertThat(registerCalls).hasSize(1);
    }

    @Test
    void onDoormanSliceRemoved_noFightBack_whenRunning() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.onDoormanSliceRemoved("ns", "svc");
        assertThat(registerCalls).isEmpty();
    }

    // -------------------------------------------------------------------------
    // beginScalingDown — idle detector entry point
    // -------------------------------------------------------------------------

    @Test
    void beginScalingDown_runningApp_transitionsToScalingDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        patches.clear();

        registry.beginScalingDown("ns", "svc");

        var app = registry.byServiceName("ns", "svc").orElseThrow();
        assertThat(app.currentState()).isInstanceOf(ServiceState.ScalingDown.class);
        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.ScalingDown);
    }

    @Test
    void beginScalingDown_callsScaleDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));

        registry.beginScalingDown("ns", "svc");

        assertThat(scaleDownCalls).hasSize(1);
        assertThat(scaleDownCalls.get(0)).isEqualTo(new ScaleDownCall("ns", "dep"));
    }

    @Test
    void beginScalingDown_unknownService_noEffect() {
        // must not throw; nothing added to registry
        registry.beginScalingDown("ns", "not-managed");
        assertThat(patches).isEmpty();
        assertThat(scaleDownCalls).isEmpty();
    }

    @Test
    void beginScalingDown_alreadyScalingDown_noDoubleTransition() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.beginScalingDown("ns", "svc"); // first call → ScalingDown + patch + scaleDown
        patches.clear();
        scaleDownCalls.clear();

        registry.beginScalingDown("ns", "svc"); // second call → no-op
        assertThat(patches).as("no patch when already ScalingDown").isEmpty();
        assertThat(scaleDownCalls).as("scaleDown not called twice").isEmpty();
    }

    // -------------------------------------------------------------------------
    // register() wiring — called after confirmScaledDown transitions
    // -------------------------------------------------------------------------

    @Test
    void onDeploymentChanged_callsRegisterAfterConfirmScaledDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow().beginScalingDown();
        registerCalls.clear();

        registry.onDeploymentChanged(deployment("ns", "dep", 0, 0));

        assertThat(registerCalls).hasSize(1);
        assertThat(registerCalls.get(0)).isEqualTo(new RegisterCall("ns", "svc"));
    }

    @Test
    void onRealEndpointsDrained_callsRegisterAfterConfirmScaledDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow().beginScalingDown();
        registerCalls.clear();

        registry.onRealEndpointsDrained("ns", "svc");

        assertThat(registerCalls).hasSize(1);
        assertThat(registerCalls.get(0)).isEqualTo(new RegisterCall("ns", "svc"));
    }

    @Test
    void onRealSlicesDrained_callsRegisterAfterConfirmScaledDown() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        registry.byServiceName("ns", "svc").orElseThrow().beginScalingDown();
        registerCalls.clear();

        registry.onRealSlicesDrained("ns", "svc");

        assertThat(registerCalls).hasSize(1);
        assertThat(registerCalls.get(0)).isEqualTo(new RegisterCall("ns", "svc"));
    }

    @Test
    void onPolicyAdded_callsRegisterWhenInitialStateIsScaledDown() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        assertThat(registerCalls).hasSize(1);
        assertThat(registerCalls.get(0)).isEqualTo(new RegisterCall("ns", "svc"));
    }

    @Test
    void onPolicyAdded_doesNotCallRegisterWhenInitialStateIsRunning() {
        registry.onPolicyAdded(policy("ns", "pol", "svc", "dep"));
        assertThat(registerCalls).isEmpty();
    }

    // -------------------------------------------------------------------------
    // awaitReady() — ScalingUp status patch (TASK-008)
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_scaledDown_patchesStatusToScalingUp() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        patches.clear();

        reg.awaitReady("ns", "svc");

        assertThat(patches).hasSize(1);
        assertThat(patches.get(0).phase()).isEqualTo(ScalingPolicyPhase.ScalingUp);
    }

    @Test
    void awaitReady_scalingUp_doesNotPatchAgain() {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 3));
        reg.awaitReady("ns", "svc");
        patches.clear();

        reg.awaitReady("ns", "svc"); // second call — already ScalingUp
        assertThat(patches).as("no second ScalingUp patch on repeat call").isEmpty();
    }

    // -------------------------------------------------------------------------
    // onDeploymentChanged — deregister before confirmRunning (TASK-008)
    // -------------------------------------------------------------------------

    @Test
    void onDeploymentReady_callsDeregisterBeforeCompletingFuture() throws Exception {
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 1));
        var future = reg.awaitReady("ns", "svc");
        assertThat(future).isNotDone();

        reg.onDeploymentChanged(deployment("ns", "dep", 1, 1));

        assertThat(future).isCompletedWithValue(null);
        assertThat(deregisterCalls).hasSize(1);
        assertThat(deregisterCalls.get(0)).isEqualTo(new RegisterCall("ns", "svc"));
    }

    @Test
    void onDeploymentReady_deregisterCalledEvenIfAlreadyRunning() {
        // Second deployment-ready event — confirmRunning returns false (already Running),
        // but deregister is still called (idempotent cleanup)
        var reg = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)));
        reg.onPolicyAdded(policy("ns", "pol", "svc", "dep", ScalingPolicyPhase.ScaledDown, 1));
        reg.awaitReady("ns", "svc");
        reg.onDeploymentChanged(deployment("ns", "dep", 1, 1)); // first ready event → Running
        deregisterCalls.clear();

        reg.onDeploymentChanged(deployment("ns", "dep", 1, 1)); // second ready event
        assertThat(deregisterCalls).hasSize(1); // deregister still called
    }
}
