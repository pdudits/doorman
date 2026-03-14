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
     * AC#4: Doorman endpoint IP is reachable from inside k3s container.
     * Uses {@code wget} (available in k3s busybox) to verify that the proxy port is TCP-reachable
     * via {@code host.testcontainers.internal} (added by K3sClusterExtension via Docker host-gateway).
     * Busybox wget exits with code 4 on network failure (can't connect); any other exit code
     * (0 = OK, 1 = server error like 404) confirms TCP connectivity was established.
     */
    @Test
    void doorman_endpoint_ip_reachable_from_k3s() throws Exception {
        int port = HARNESS.proxyPort();

        // Exit code 4 = network failure (couldn't connect); 0/1 = connected (200 or server error like 404)
        var result = K3S.execInContainer("sh", "-c",
                "wget -q -T 5 -O/dev/null http://host.testcontainers.internal:" + port + "/; " +
                "[ $? -ne 4 ] && echo REACHABLE || echo UNREACHABLE");

        assertThat(result.getStdout().trim())
                .as("Expected proxy at host.testcontainers.internal:%d to be TCP-reachable from inside k3s", port)
                .isEqualTo("REACHABLE");
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
