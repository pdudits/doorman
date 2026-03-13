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

package io.zeromagic.doorman.proxy;

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.kubernetes.DeploymentStateReader;
import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import io.zeromagic.doorman.scaling.ScaledApplicationRegistry;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyServerTest {

    private static final String NS  = "default";
    private static final String SVC = "my-svc";
    private static final String DEP = "my-dep";

    // The .test TLD resolves to loopback via TestDotResolverProvider (JEP-418 SPI)
    private static final String HOST = "example.test";

    private TestKubernetesFacade facade;
    private IngressRouteIndex routeIndex;
    private ScaledApplicationRegistry registry;
    private ProxyServer server;
    private HttpClient client;

    /** Reader that returns a scaled-down deployment (0 replicas). */
    private static final DeploymentStateReader SCALED_DOWN_READER =
            (ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(0, 0));

    record SimpleResponse(int statusCode, String location) {}

    @BeforeEach
    void setUp() {
        facade     = new TestKubernetesFacade();
        routeIndex = new IngressRouteIndex(facade);
        registry   = registryWith(SCALED_DOWN_READER);
        client     = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @AfterEach
    void tearDown() {
        if (server != null) server.stop();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScaledApplicationRegistry registryWith(DeploymentStateReader reader) {
        return new ScaledApplicationRegistry(
                (ns, name, phase, target, msg) -> {},
                new ServiceScaler() {
                    @Override public void scaleUp(String ns, String dep, int reps) {}
                    @Override public void scaleDown(String ns, String dep) {}
                },
                new EndpointRegistrar() {
                    @Override public void register(String ns, String svc) {}
                    @Override public void deregister(String ns, String svc) {}
                },
                reader,
                Duration.ofMinutes(5)
        );
    }

    /** Starts the proxy server with the given timeout and returns its bound port. */
    private int startServer(long timeoutMillis) {
        server = new ProxyServer(routeIndex, registry, timeoutMillis);
        server.start();
        return server.boundPort();
    }

    /** Registers a route (host=example.test, path=/) pointing to NS/SVC. */
    private void addRoute() {
        Ingress ingress = new IngressBuilder()
                .withNewMetadata().withNamespace(NS).withName("my-ingress").endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(HOST)
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(SVC)
                                        .withNewPort().withNumber(80).endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
        facade.stubIngress(ingress);

        var spec = new ScalingPolicySpec();
        spec.setServiceName(SVC);
        spec.setDeploymentName(DEP);
        spec.setIngressName("my-ingress");
        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(NS).withName("my-policy").build());
        policy.setSpec(spec);
        routeIndex.onAdded(policy);
    }

    /** Adds a policy to the registry in ScaledDown state. */
    private ScalingPolicy scaledDownPolicy() {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SVC);
        spec.setDeploymentName(DEP);
        spec.setIngressName("my-ingress");
        var policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder().withNamespace(NS).withName("my-policy").build());
        policy.setSpec(spec);
        var status = new ScalingPolicyStatus();
        status.setPhase(ScalingPolicyPhase.ScaledDown);
        status.setTargetReplicas(1);
        policy.setStatus(status);
        return policy;
    }

    private SimpleResponse get(int port, String host, String path) throws Exception {
        var resp = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://" + host + ":" + port + path))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.discarding());
        return new SimpleResponse(resp.statusCode(), resp.headers().firstValue("Location").orElse(null));
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void unknownRoute_returns404() throws Exception {
        // No route registered — expect 404
        int port = startServer(5_000);
        var resp = get(port, "unknown.test", "/");
        assertThat(resp.statusCode()).isEqualTo(404);
    }

    @Test
    void runningService_returns307_immediately() throws Exception {
        // Service is already Running (readyReplicas=1) when onAdded fires
        registry = registryWith((ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(1, 1)));
        addRoute();
        registry.onAdded(scaledDownPolicy()); // onAdded with reader returning ready → transitions to Running

        int port = startServer(5_000);
        var resp = get(port, HOST, "/some/path?q=1");

        assertThat(resp.statusCode()).isEqualTo(307);
        assertThat(resp.location()).isEqualTo("http://" + HOST + ":" + port + "/some/path?q=1");
    }

    @Test
    void scaledDownService_returns307_after_future_completes() throws Exception {
        // Service starts in ScaledDown; another thread fires onDeploymentChanged to complete the future
        addRoute();
        registry.onAdded(scaledDownPolicy());

        int port = startServer(5_000);

        var readyDeployment = new DeploymentBuilder()
                .withMetadata(new ObjectMetaBuilder().withNamespace(NS).withName(DEP).build())
                .withNewSpec().withReplicas(1).endSpec()
                .withNewStatus().withReplicas(1).withReadyReplicas(1).endStatus()
                .build();

        // Fire scale-up completion after a small delay on another thread
        var executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> registry.onDeploymentChanged(readyDeployment), 200, TimeUnit.MILLISECONDS);

        var resp = get(port, HOST, "/");

        assertThat(resp.statusCode()).isEqualTo(307);
        assertThat(resp.location()).isEqualTo("http://" + HOST + ":" + port + "/");
        executor.shutdownNow();
    }

    @Test
    void scaleUpTimeout_returns503() throws Exception {
        // Service starts ScaledDown; nothing completes the future within the 150ms timeout
        addRoute();
        registry.onAdded(scaledDownPolicy());

        int port = startServer(150); // very short timeout
        var resp = get(port, HOST, "/");

        assertThat(resp.statusCode()).isEqualTo(503);
    }

    @Test
    void awaitReady_fails_returns502() throws Exception {
        // onDeleted removes service from registry → awaitReady returns failedFuture
        addRoute();
        registry.onAdded(scaledDownPolicy());
        registry.onDeleted(scaledDownPolicy());

        int port = startServer(5_000);
        var resp = get(port, HOST, "/");

        assertThat(resp.statusCode()).isEqualTo(502);
    }
}
