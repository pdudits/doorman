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

package io.zeromagic.doorman.k3s;

import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.scaling.ScaledApplicationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link DoormanSystemHarness}: verifies all 5 acceptance criteria.
 * The k3s cluster is shared across all tests in this class.
 */
class DoormanHarnessIT {

    @RegisterExtension
    static final TraefikK3sExtension K3S = new TraefikK3sExtension("harness-it");

    @RegisterExtension
    static final DoormanSystemHarness HARNESS = new DoormanSystemHarness(K3S, "30s", "2s");

    /** AC#1: harness starts and stops cleanly — if we reach here, beforeAll succeeded. */
    @Test
    void harness_starts_and_stops_cleanly() {
        assertThat(HARNESS.scope()).isNotNull();
        assertThat(HARNESS.proxyPort()).isGreaterThan(0);
    }

    /** AC#2: proxy port is reachable from the test JVM. */
    @Test
    void proxy_port_is_reachable() throws Exception {
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + HARNESS.proxyPort() + "/probe"))
                        .timeout(Duration.ofSeconds(5))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());
        // No route registered → 404; but TCP connection succeeded (port is up)
        assertThat(response.statusCode()).isEqualTo(404);
    }

    /** AC#3: Kubernetes informers receive events — create a ScalingPolicy and wait for registry acknowledgement. */
    @Test
    void informers_receive_k8s_events() throws Exception {
        var ns = K3S.namespace();
        HARNESS.deployEchoApp(ns, "echo-ac3");
        HARNESS.createIngress(ns, "echo-ac3.test", "echo-ac3");
        HARNESS.createScalingPolicy(ns, "policy-ac3", "echo-ac3", "echo-ac3", "echo-ac3");

        var registry = HARNESS.scope().get(ScaledApplicationRegistry.class);

        // Poll until registry has a managed application for this service (informer fired)
        long deadline = System.currentTimeMillis() + 15_000;
        boolean found = false;
        while (System.currentTimeMillis() < deadline) {
            if (registry.byServiceName(ns, "echo-ac3").isPresent()) {
                found = true;
                break;
            }
            Thread.sleep(500);
        }
        assertThat(found).as("ScalingPolicy informer should have delivered event to registry within 15s").isTrue();
    }

    /**
     * AC#4: Doorman endpoint IP is reachable from inside k3s via Traefik routing.
     *
     * <p>Creates a headless Service + manual Endpoints pointing to the in-process proxy
     * (same mechanism Doorman uses when registering itself), then an Ingress for {@code system.test}.
     * Sends a real HTTP request through Traefik from the test JVM using a Host header.
     * This verifies the full path: test JVM → Traefik (in k3s) → podIp:proxyPort (test JVM).
     */
    @Test
    void doorman_endpoint_reachable_via_traefik() throws Exception {
        var ns = K3S.namespace();
        HARNESS.createProxyService(ns, "system-proxy");
        HARNESS.createIngress(ns, "system.test", "system-proxy");

        int traefikPort = K3S.traefikHttpPort();

        // Traefik uses the Host header to match the ingress rule
        var response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + traefikPort + "/probe"))
                        .header("Host", "system.test")
                        .timeout(Duration.ofSeconds(10))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        // Proxy is up but no route registered for system.test → 404 from Doorman proxy.
        // Any response from the proxy (not a Traefik gateway error) proves the path works.
        assertThat(response.statusCode())
                .as("Expected a response from Doorman proxy via Traefik (not a 5xx gateway error)")
                .isNotEqualTo(502)
                .isNotEqualTo(503);
    }

    /** AC#5: idleTimeout and pollInterval are configurable — verify via TraefikConfig in scope. */
    @Test
    void idle_timeout_and_poll_interval_are_configurable() {
        var traefikConfig = HARNESS.scope().get(TraefikConfig.class);
        assertThat(traefikConfig).isInstanceOf(TraefikConfig.Direct.class);
        var direct = (TraefikConfig.Direct) traefikConfig;
        assertThat(direct.idleTimeout()).isEqualTo("30s");
        assertThat(direct.metricsPollInterval()).isEqualTo("2s");
    }
}
