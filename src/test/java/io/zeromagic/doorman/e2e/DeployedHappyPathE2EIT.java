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

import io.zeromagic.doorman.k3s.DeployedDoormanK3sExtension;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import org.junit.jupiter.api.Tag;
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
 * Blackbox end-to-end happy path test for Doorman.
 *
 * <p>Doorman runs as a real pod inside k3s, deployed from the {@code deploy/} manifests. The test
 * only speaks to Traefik — no in-process Doorman components. The scenario is identical to
 * {@link HappyPathE2EIT} but validates the production image and RBAC configuration.
 *
 * <p>Requires the {@code blackbox} Maven profile ({@code mvn verify -P blackbox}) which saves the
 * Docker image to a tar file and passes its path via the {@code doorman.image.tar} system property.
 */
@Tag("blackbox")
class DeployedHappyPathE2EIT {

    private static final Logger LOG = LoggerFactory.getLogger(DeployedHappyPathE2EIT.class);

    @RegisterExtension
    static final DeployedDoormanK3sExtension K3S = new DeployedDoormanK3sExtension("e2e-deployed", "10s");

    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    @Test
    void happy_path_blackbox() throws Exception {
        String ns = K3S.namespace();
        String appName = "echo-bb";
        String host = appName + ".test";
        String policyName = appName;

        // ── Phase 1: Setup ─────────────────────────────────────────────────────
        LOG.info("=== Phase 1: Setup — deploying echo app, ingress, and scaling policy ===");
        K3S.deployEchoApp(ns, appName);
        K3S.createIngress(ns, host, appName);
        K3S.createScalingPolicy(ns, policyName, appName, appName, appName);
        K3S.awaitPodReady(ns, "app=" + appName, 120);

        // Wait for Traefik reconciliation before sending baseline traffic
        Thread.sleep(3_000);

        // ── Phase 2: Baseline — initial traffic reaches echo ───────────────────
        LOG.info("=== Phase 2: Baseline — verifying initial GET returns 200 ===");
        // Use the test hostname so the Host header carries the port (host:PORT).
        // TestDotResolverProvider resolves *.test to 127.0.0.1, so the connection
        // still reaches Traefik on the mapped port. Including the port in the Host
        // header lets Doorman's 307 Location include the correct port, avoiding the
        // need for a fixed port 80 binding.
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
        K3S.awaitScalingPolicyPhase(ns, policyName, ScalingPolicyPhase.ScaledDown, 60);
        K3S.awaitDeploymentReplicas(ns, appName, 0, 15);
        LOG.info("Echo deployment scaled to 0, Doorman registered as endpoint");

        // Wait for Traefik to propagate endpoint change
        Thread.sleep(3_000);

        // ── Phase 4: Hold + scale-up — send request through Doorman ───────────
        LOG.info("=== Phase 4: Sending held request — will block until echo is back ===");
        var heldResp = http.send(
                HttpRequest.newBuilder(URI.create(traefikBase + "/held-path"))
                        .timeout(Duration.ofSeconds(120))
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

        // ── Phase 5: Running — policy should be back in Running state ──────────
        LOG.info("=== Phase 5: Awaiting ScalingPolicy Running ===");
        K3S.awaitScalingPolicyPhase(ns, policyName, ScalingPolicyPhase.Running, 60);
        LOG.info("=== Blackbox happy path completed successfully ===");
    }
}
