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

import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

/**
 * Smoke tests verifying that {@link TraefikK3sExtension} correctly exposes Traefik HTTP routing
 * and Prometheus metrics from a k3s Testcontainers cluster.
 *
 * <p>Uses {@code mendhak/http-https-echo} as the backend server because it reflects the request
 * method and body back in the JSON response — the same echo server will be reused in E2E tests
 * to verify that Doorman's 307 redirect preserves HTTP method and request body.
 */
class TraefikSmokeIT {

    private static final String ECHO_SERVICE = "echo-smoke";
    private static final String ECHO_HOST    = "echo-smoke.test";
    private static final String ECHO_IMAGE   = "mendhak/http-https-echo:latest";

    @RegisterExtension
    static final TraefikK3sExtension K3S = new TraefikK3sExtension("traefik-smoke-it");

    @Test
    void echo_server_accessible_through_traefik() throws Exception {
        var ns     = K3S.namespace();
        var client = K3S.client();

        client.apps().deployments().inNamespace(ns).resource(
                new DeploymentBuilder()
                        .withNewMetadata().withName(ECHO_SERVICE).withNamespace(ns).endMetadata()
                        .withNewSpec()
                            .withReplicas(1)
                            .withNewSelector().addToMatchLabels("app", ECHO_SERVICE).endSelector()
                            .withNewTemplate()
                                .withNewMetadata().addToLabels("app", ECHO_SERVICE).endMetadata()
                                .withNewSpec()
                                    .addNewContainer()
                                        .withName("echo")
                                        .withImage(ECHO_IMAGE)
                                        .addNewPort().withContainerPort(8080).endPort()
                                    .endContainer()
                                .endSpec()
                            .endTemplate()
                        .endSpec()
                        .build()
        ).create();

        client.services().inNamespace(ns).resource(
                new ServiceBuilder()
                        .withNewMetadata().withName(ECHO_SERVICE).withNamespace(ns).endMetadata()
                        .withNewSpec()
                            .addToSelector("app", ECHO_SERVICE)
                            .addNewPort()
                                .withPort(80)
                                .withTargetPort(new IntOrString(8080))
                                .withName("http")
                            .endPort()
                        .endSpec()
                        .build()
        ).create();

        client.network().v1().ingresses().inNamespace(ns).resource(
                new IngressBuilder()
                        .withNewMetadata().withName(ECHO_SERVICE).withNamespace(ns).endMetadata()
                        .withNewSpec()
                            .addNewRule()
                                .withHost(ECHO_HOST)
                                .withNewHttp()
                                    .addNewPath()
                                        .withPath("/")
                                        .withPathType("Prefix")
                                        .withNewBackend()
                                            .withNewService()
                                                .withName(ECHO_SERVICE)
                                                .withNewPort().withNumber(80).endPort()
                                            .endService()
                                        .endBackend()
                                    .endPath()
                                .endHttp()
                            .endRule()
                        .endSpec()
                        .build()
        ).create();

        awaitPodReady(ns, "app", ECHO_SERVICE);

        // TestDotResolverProvider resolves echo-smoke.test → loopback;
        // HttpClient sets Host: echo-smoke.test automatically (Traefik strips port for host matching)
        var httpClient = HttpClient.newHttpClient();
        var url        = URI.create("http://" + ECHO_HOST + ":" + K3S.traefikHttpPort() + "/probe");
        var body       = "{\"hello\":\"doorman\"}";

        String responseBody = retryUntilSuccess(httpClient, url, body, Duration.ofSeconds(30));

        assertThat(responseBody).as("echoed method").contains("POST");
        assertThat(responseBody).as("echoed body payload").contains("doorman");
    }

    @Test
    void traefik_prometheus_metrics_available() throws Exception {
        var httpClient = HttpClient.newHttpClient();
        var response   = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(K3S.traefikMetricsUrl()))
                        .GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).as("metrics HTTP status").isEqualTo(200);
        assertThat(response.body()).as("metrics body").contains("traefik_");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String retryUntilSuccess(HttpClient httpClient, URI url, String body, Duration timeout)
            throws Exception {
        var deadline = Instant.now().plus(timeout);
        Exception last = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                var response = httpClient.send(
                        HttpRequest.newBuilder()
                                .uri(url)
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(body))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    return response.body();
                }
            } catch (Exception e) {
                last = e;
            }
            Thread.sleep(1_000);
        }
        if (last != null) throw last;
        fail("No 200 response from " + url + " within " + timeout);
        return null; // unreachable
    }

    private void awaitPodReady(String ns, String labelKey, String labelValue)
            throws InterruptedException {
        var deadline = Instant.now().plusSeconds(120);
        while (Instant.now().isBefore(deadline)) {
            var pods = K3S.client().pods().inNamespace(ns)
                    .withLabel(labelKey, labelValue).list().getItems();
            if (!pods.isEmpty() && pods.stream().allMatch(this::isPodReady)) return;
            Thread.sleep(2_000);
        }
        throw new IllegalStateException(
                "Pod with label " + labelKey + "=" + labelValue + " did not become ready within 120s");
    }

    private boolean isPodReady(Pod pod) {
        var status = pod.getStatus();
        if (status == null || status.getConditions() == null) return false;
        return status.getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
