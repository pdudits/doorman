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

package io.zeromagic.doorman.e2e;

import io.zeromagic.doorman.k3s.DoormanSystemHarness;
import io.zeromagic.doorman.k3s.TraefikK3sExtension;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full end-to-end happy path test for Doorman:
 * <ol>
 *   <li>Deploy echo service, create ScalingPolicy with short idle timeout</li>
 *   <li>Verify baseline traffic flows through Traefik to echo (HTTP 200)</li>
 *   <li>Wait for idle detection → Doorman scales echo to 0 and registers itself as endpoint</li>
 *   <li>Send request through Traefik — Doorman holds it and triggers scale-up</li>
 *   <li>Client (with followRedirects=NORMAL) follows 307(s) until echo is back, assert HTTP 200</li>
 *   <li>Verify ScalingPolicy status = Running</li>
 * </ol>
 *
 * <p>Note: The held request may receive multiple 307 redirects if Traefik hasn't fully propagated
 * the endpoint change when Doorman issues the first redirect.  {@code HttpClient.Redirect.NORMAL}
 * transparently follows all of them until echo eventually returns 200.
 */
class HappyPathE2EIT {

    private static final Logger LOG = LoggerFactory.getLogger(HappyPathE2EIT.class);

    /** Short idle timeout so the test doesn't take too long waiting for scale-down. */
    @RegisterExtension
    static final TraefikK3sExtension K3S = new TraefikK3sExtension("e2e-happy-path");

    @RegisterExtension
    static final DoormanSystemHarness HARNESS = new DoormanSystemHarness(K3S, "10s", "2s");

    // HttpClient that follows redirects — makes the held-request transparent to the caller
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Test
    void happy_path_end_to_end() throws Exception {
        String ns = K3S.namespace();
        String appName = "echo-e2e";
        String policyName = "echo-e2e";
        String host = appName + ".test";

        // ── Phase 1: Setup ─────────────────────────────────────────────────────
        LOG.info("=== Phase 1: Setup — deploying echo app, ingress, and scaling policy ===");
        HARNESS.deployEchoApp(ns, appName);
        HARNESS.createIngress(ns, host, appName);
        HARNESS.createScalingPolicy(ns, policyName, appName, appName, appName);
        HARNESS.awaitPodReady(ns, "app=" + appName, 120);

        // as much as I hate sleeps, it does take time for traefik to catch up.
        Thread.sleep(Duration.ofMillis(250));

        // ── Phase 2: Baseline — initial traffic reaches echo ───────────────────
        LOG.info("=== Phase 2: Baseline — verifying initial GET returns 200 ===");
        String traefikBase = "http://" + host + ":" + K3S.traefikHttpPort();
        var baselineResp = http.send(
                HttpRequest.newBuilder(URI.create(traefikBase + "/baseline"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(baselineResp.statusCode())
                .as("baseline GET to echo should return 200")
                .isEqualTo(200);

        // ── Phase 3: Scale-down — Doorman detects idle and scales to 0 ─────────
        LOG.info("=== Phase 3: Waiting for ScaledDown (idle timeout=10s) ===");
        HARNESS.awaitScalingPolicyPhase(ns, policyName, ScalingPolicyPhase.ScaledDown, 60);
        HARNESS.awaitDeploymentReplicas(ns, appName, 0, 15);
        LOG.info("Echo deployment scaled to 0, Doorman registered as endpoint");
        Thread.sleep(Duration.ofMillis(250));
        // ── Phase 4: Hold + scale-up — send request through Doorman ───────────
        // The request blocks in Doorman's proxy until the echo pod is ready.
        // Doorman automatically triggers scale-up when awaitReady() is called.
        // HttpClient follows 307 redirect(s) automatically.
        // scaleUpTimeout in the harness is 30s; echo image may need pulling → allow generous wall time.
        LOG.info("=== Phase 4: Sending held request — will block until echo is back ===");
        var heldResp = http.send(
                HttpRequest.newBuilder(URI.create(traefikBase + "/held-path"))
                        .timeout(java.time.Duration.ofSeconds(120))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        LOG.info("Phase 4 response: status={} finalUri={} Server={} body={}",
                heldResp.statusCode(),
                heldResp.uri(),
                heldResp.headers().firstValue("Server").orElse("(none)"),
                heldResp.body().substring(0, Math.min(200, heldResp.body().length())));
        assertThat(heldResp.statusCode())
                .as("held request should ultimately reach echo and return 200")
                .isEqualTo(200);
        LOG.info("Held request completed with status {} URI={}", heldResp.statusCode(), heldResp.uri());

        // ── Phase 5: Running — policy should be back in Running state ──────────
        LOG.info("=== Phase 5: Awaiting ScalingPolicy Running ===");
        HARNESS.awaitScalingPolicyPhase(ns, policyName, ScalingPolicyPhase.Running, 60);
        LOG.info("=== Happy path completed successfully ===");
    }
}
