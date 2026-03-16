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
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.scaling.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import io.zeromagic.doorman.scaling.ScaledApplicationRegistry;
import io.zeromagic.doorman.scaling.ServiceState;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IdleDetectorTest {

    // ── Constants ─────────────────────────────────────────────────────────────

    private static final String NS    = "test-ns";
    private static final String SVC   = "test-svc";
    private static final String DEPLOY = "test-dep";
    private static final String INGRESS = "test-ingress";
    private static final int    PORT   = 8080;
    static final String LABEL = NS + "-" + SVC + "-" + PORT + "@kubernetes";
    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    // ── Test doubles ──────────────────────────────────────────────────────────

    /** Controllable clock for deterministic time-based assertions. */
    static final class MutableClock extends Clock {
        private Instant now;
        MutableClock(Instant start) { this.now = start; }
        void advance(Duration d)    { this.now = now.plus(d); }
        @Override public Instant instant()          { return now; }
        @Override public ZoneId getZone()           { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone){ return this; }
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private MutableClock clock;
    private TestKubernetesFacade facade;
    private TraefikServiceNameResolver resolver;
    private ScaledApplicationRegistry registry;
    private IdleDetector detector;

    @BeforeEach
    void setUp() {
        clock   = new MutableClock(Instant.EPOCH);
        facade  = new TestKubernetesFacade();
        facade.stubIngress(ingress(INGRESS, SVC, PORT));

        resolver = new TraefikServiceNameResolver(facade);

        registry = new ScaledApplicationRegistry(
                (ns, name, phase, target, msg) -> {},
                new ServiceScaler() {
                    @Override public void scaleUp(String n, String d, int r) {}
                    @Override public void scaleDown(String n, String d) {}
                },
                new EndpointRegistrar() {
                    @Override public void register(String n, String s) {}
                    @Override public void deregister(String n, String s) {}
                },
                (ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(1, 1)),
                TIMEOUT
        );

        var config = new TraefikConfig.Direct("http://fake-metrics", "5m", "15s");
        detector = new IdleDetector(null, resolver, registry, config, clock);
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void firstPoll_storesBaseline_noScaleDown() {
        register(SVC);

        detector.evaluateAll(Map.of(LABEL, 100.0));

        // No scale-down on first observation — just baseline
        assertRunning(SVC);
    }

    @Test
    void trafficSeen_resetsIdleSince() {
        register(SVC);
        detector.evaluateAll(Map.of(LABEL, 100.0)); // baseline
        detector.evaluateAll(Map.of(LABEL, 100.0)); // no traffic → idleSince = T0

        // Traffic arrives
        detector.evaluateAll(Map.of(LABEL, 150.0)); // delta > 0 → reset

        // Advance well past timeout — still should not scale down because idle was reset
        clock.advance(TIMEOUT.plusSeconds(10));
        detector.evaluateAll(Map.of(LABEL, 150.0)); // new baseline, idleSince set to now
        // idleSince is NOW, not T0, so duration < 0 — no scale-down
        assertRunning(SVC);
    }

    @Test
    void noTraffic_advancesIdleTimer_belowThreshold_noScaleDown() {
        register(SVC);
        detector.evaluateAll(Map.of(LABEL, 42.0)); // baseline
        clock.advance(Duration.ofMinutes(2));
        detector.evaluateAll(Map.of(LABEL, 42.0)); // idle since T0 + 0 = T0; 2m < 5m

        assertRunning(SVC);
    }

    @Test
    void idleTimeoutReached_triggersScaleDown() {
        register(SVC);
        detector.evaluateAll(Map.of(LABEL, 42.0)); // baseline
        detector.evaluateAll(Map.of(LABEL, 42.0)); // idleSince = T0 (delta == 0)

        clock.advance(TIMEOUT);                     // now = T0 + 5m ≥ timeout
        detector.evaluateAll(Map.of(LABEL, 42.0));

        assertThat(registry.byServiceName(NS, SVC).orElseThrow().currentState())
                .as("should have transitioned to ScalingDown")
                .isInstanceOf(ServiceState.ScalingDown.class);
    }

    @Test
    void counterReset_treatedAsTraffic_resetsIdleSince() {
        register(SVC);
        detector.evaluateAll(Map.of(LABEL, 200.0)); // baseline = 200
        detector.evaluateAll(Map.of(LABEL, 200.0)); // idle started (idleSince = T0)
        clock.advance(TIMEOUT);

        // Counter reset (pod restart) — current is lower than last
        detector.evaluateAll(Map.of(LABEL, 10.0));  // reset guard → treat as traffic

        // Idle clock restarted; should NOT scale down yet
        assertRunning(SVC);
    }

    @Test
    void nonRunningService_isSkipped() {
        // Service registered with 0 replicas → Stopped state
        var stoppedRegistry = new ScaledApplicationRegistry(
                (ns, name, phase, target, msg) -> {},
                new ServiceScaler() {
                    @Override public void scaleUp(String n, String d, int r) {}
                    @Override public void scaleDown(String n, String d) {}
                },
                new EndpointRegistrar() {
                    @Override public void register(String n, String s) {}
                    @Override public void deregister(String n, String s) {}
                },
                (ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0)),
                TIMEOUT
        );
        var policy = policy(SVC, INGRESS, DEPLOY);
        resolver.onPolicyAdded(policy);
        stoppedRegistry.onPolicyAdded(policy);

        var det = new IdleDetector(null, resolver, stoppedRegistry,
                new TraefikConfig.Direct("http://fake", "5m", "15s"), clock);

        // Should not throw or transition
        det.evaluateAll(Map.of(LABEL, 0.0));

        assertThat(stoppedRegistry.byServiceName(NS, SVC).orElseThrow().currentState())
                .isInstanceOf(ServiceState.Stopped.class);
    }

    @Test
    void errorInOneService_doesNotBlockOthers() {
        // Second service that works fine
        var svc2 = "other-svc";
        var label2 = NS + "-" + svc2 + "-" + PORT + "@kubernetes";
        facade.stubIngress(ingress("other-ingress", svc2, PORT));

        register(SVC);
        var policy2 = policy(svc2, "other-ingress", "other-dep");
        resolver.onPolicyAdded(policy2);
        registry.onPolicyAdded(policy2);

        // Resolver that throws for SVC but works for svc2
        var throwingResolver = new TraefikServiceNameResolver(facade) {
            @Override
            public Optional<String> resolve(String ns, String svc) {
                if (SVC.equals(svc)) throw new RuntimeException("simulated resolver error");
                return super.resolve(ns, svc);
            }
        };
        // Prime the throwingResolver's internal policy index for svc2
        throwingResolver.onPolicyAdded(policy(SVC, INGRESS, DEPLOY));
        throwingResolver.onPolicyAdded(policy2);
        var det = new IdleDetector(null, throwingResolver, registry,
                new TraefikConfig.Direct("http://fake", "5m", "15s"), clock);

        // Baseline for both
        det.evaluateAll(Map.of(label2, 10.0));
        det.evaluateAll(Map.of(label2, 10.0)); // idleSince set for svc2

        clock.advance(TIMEOUT.plusSeconds(1));
        // SVC throws, svc2 should still fire beginScalingDown
        det.evaluateAll(Map.of(label2, 10.0));

        assertThat(registry.byServiceName(NS, svc2).orElseThrow().currentState())
                .as("svc2 must have scaled down despite error in SVC")
                .isInstanceOf(ServiceState.ScalingDown.class);
        assertRunning(SVC); // SVC not affected (error was caught and skipped)
    }

    @Test
    void unresolvableLabel_serviceSurvidesTheCycle() {
        // No ingress stub for this service → resolver returns empty
        var noIngressFacade = new TestKubernetesFacade(); // empty
        var res = new TraefikServiceNameResolver(noIngressFacade);
        register(SVC);
        var policy = policy(SVC, INGRESS, DEPLOY);
        res.onPolicyAdded(policy);

        var det = new IdleDetector(null, res, registry,
                new TraefikConfig.Direct("http://fake", "5m", "15s"), clock);
        clock.advance(TIMEOUT.plusSeconds(60));
        det.evaluateAll(Map.of()); // label cannot be resolved → skip

        // Still Running — missing label means we can't make a decision this cycle
        assertRunning(SVC);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void register(String svc) {
        var pol = policy(svc, INGRESS, DEPLOY);
        resolver.onPolicyAdded(pol);
        registry.onPolicyAdded(pol);
    }

    private void assertRunning(String svc) {
        assertThat(registry.byServiceName(NS, svc).orElseThrow().currentState())
                .isInstanceOf(ServiceState.Running.class);
    }

    private ScalingPolicy policy(String svc, String ingressName, String deploy) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(svc);
        spec.setDeploymentName(deploy);
        spec.setIngressName(ingressName);
        var p = new ScalingPolicy();
        p.setMetadata(new ObjectMetaBuilder().withNamespace(NS).withName(svc + "-policy").build());
        p.setSpec(spec);
        return p;
    }

    private static io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress(
            String name, String svc, int port) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(NS).withName(name).endMetadata()
                .withNewSpec()
                    .addNewRule().withNewHttp()
                        .addNewPath().withPathType("Prefix").withPath("/")
                            .withNewBackend().withNewService()
                                .withName(svc).withNewPort().withNumber(port).endPort()
                            .endService().endBackend()
                        .endPath()
                    .endHttp().endRule()
                .endSpec()
                .build();
    }
}
