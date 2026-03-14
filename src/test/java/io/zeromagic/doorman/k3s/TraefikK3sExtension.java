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

import io.fabric8.kubernetes.api.model.Pod;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.k3s.K3sContainer;

import java.time.Instant;

/**
 * JUnit 5 extension that extends {@link K3sClusterExtension} with Traefik support:
 * <ul>
 *   <li>Re-enables Traefik (the base {@code K3sContainer} starts k3s with {@code --disable=traefik}).</li>
 *   <li>Exposes Traefik's HTTP port (80) and Prometheus metrics port (9100) to the Docker host.</li>
 *   <li>Enables Traefik Prometheus metrics by applying a {@code HelmChartConfig} via the Kubernetes
 *       API after the cluster starts — the Helm controller picks it up and reconfigures Traefik.</li>
 *   <li>Forwards k3s container logs to SLF4J for diagnostics.</li>
 *   <li>Waits for Traefik pods in {@code kube-system} to be Ready before tests begin.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * @RegisterExtension
 * static final TraefikK3sExtension K3S = new TraefikK3sExtension("my-namespace");
 * }</pre>
 */
public class TraefikK3sExtension extends K3sClusterExtension {

    private static final Logger LOG = LoggerFactory.getLogger(TraefikK3sExtension.class);

    public TraefikK3sExtension(String namespace) {
        super(namespace);
    }

    @Override
    protected void configureContainer(K3sContainer container) {
        // K3sContainer defaults to --disable=traefik; override command to enable it
        container.setCommand("server", "--tls-san=" + container.getHost());
        // addExposedPort appends to K3sContainer's existing list (6443, 8443)
        container.addExposedPort(80);
        container.addExposedPort(9100);
        // Forward k3s container logs to SLF4J for post-failure forensics
        container.withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix("k3s"));
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        super.beforeAll(context);
        enableTraefikMetrics();
        awaitTraefikReady();
    }

    /** The host port mapped to Traefik's HTTP listener (container port 80). */
    public int traefikHttpPort() {
        return getMappedPort(80);
    }

    /** URL to Traefik's Prometheus metrics endpoint. */
    public String traefikMetricsUrl() {
        return "http://localhost:" + getMappedPort(9100) + "/metrics";
    }

    private void enableTraefikMetrics() {
        try (var yaml = getClass().getResourceAsStream("/traefik-metrics.yaml")) {
            if (yaml == null) throw new IllegalStateException("traefik-metrics.yaml not found on classpath");
            client().load(yaml).create();
            LOG.info("Applied Traefik metrics HelmChartConfig — waiting for Helm controller to reconcile");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to apply Traefik metrics HelmChartConfig", e);
        }
    }

    private void awaitTraefikReady() throws InterruptedException {
        LOG.info("Waiting for Traefik to be ready in kube-system...");
        Instant deadline = Instant.now().plusSeconds(240);
        while (Instant.now().isBefore(deadline)) {
            // Use label selector to find only the Traefik deployment pod.
            // helm-install-traefik-* Job pods complete and are never "Ready"; svclb-traefik-*
            // is the klipper-lb pod. Both would cause allReady=false indefinitely.
            var traefikPods = client().pods().inNamespace("kube-system")
                    .withLabel("app.kubernetes.io/name", "traefik")
                    .list().getItems();
            if (!traefikPods.isEmpty()) {
                boolean allReady = traefikPods.stream().allMatch(this::isPodReady);
                LOG.info("Traefik pod(s): {} — allReady={}",
                        traefikPods.stream().map(p -> p.getMetadata().getName()).toList(), allReady);
                if (allReady) {
                    LOG.info("Traefik is ready");
                    return;
                }
            } else {
                var allPods = client().pods().inNamespace("kube-system").list().getItems();
                LOG.info("No Traefik pods (label app.kubernetes.io/name=traefik) yet. kube-system pods: {}",
                        allPods.stream().map(p -> p.getMetadata().getName()).toList());
            }
            Thread.sleep(5_000);
        }
        throw new IllegalStateException("Traefik did not become ready within 240s");
    }

    private boolean isPodReady(Pod pod) {
        var status = pod.getStatus();
        if (status == null || status.getConditions() == null) return false;
        return status.getConditions().stream()
                .anyMatch(c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
    }
}
