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

import io.avaje.inject.BeanScope;
import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.cli.CliArgs;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.cli.KubernetesConfig;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Map;

/**
 * JUnit 5 extension that wires the full Doorman component graph in-process against a live k3s
 * cluster. Uses avaje's {@link BeanScope} with only two external substitutions:
 * <ul>
 *   <li>A {@link CliArgs} instance populated via Picocli parsing (same path as {@code Main.java})
 *       — sets pod IP to the Docker bridge gateway, proxy port to a free ephemeral port, and
 *       Traefik metrics URL to the k3s container's mapped port.</li>
 *   <li>A {@link KubernetesConfig.Raw} wrapping the k3s kubeconfig YAML so
 *       {@code KubernetesClientFacade} connects to the test cluster.</li>
 * </ul>
 *
 * <p>Declare after {@link TraefikK3sExtension} so JUnit 5 runs them in order:
 * <pre>{@code
 * @RegisterExtension
 * static final TraefikK3sExtension K3S = new TraefikK3sExtension("my-ns");
 * @RegisterExtension
 * static final DoormanSystemHarness HARNESS = new DoormanSystemHarness(K3S);
 * }</pre>
 */
public class DoormanSystemHarness implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(DoormanSystemHarness.class);

    private static final String DEFAULT_IDLE_TIMEOUT = "30s";
    private static final String DEFAULT_POLL_INTERVAL = "2s";

    private final TraefikK3sExtension ext;
    private final String idleTimeout;
    private final String pollInterval;

    private BeanScope scope;
    private int proxyPort;

    public DoormanSystemHarness(TraefikK3sExtension ext) {
        this(ext, DEFAULT_IDLE_TIMEOUT, DEFAULT_POLL_INTERVAL);
    }

    public DoormanSystemHarness(TraefikK3sExtension ext, String idleTimeout, String pollInterval) {
        this.ext = ext;
        this.idleTimeout = idleTimeout;
        this.pollInterval = pollInterval;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        proxyPort = findFreePort();
        LOG.info("Doorman proxy will bind to port {}", proxyPort);

        // Resolve the IP of host.testcontainers.internal from inside the k3s container.
        // K3sClusterExtension adds host.testcontainers.internal via Docker's host-gateway,
        // which on Mac Docker Desktop resolves to the Docker Desktop VM gateway (e.g. 192.168.65.254)
        // — the IP that IS routable from inside containers to the Mac host (0.0.0.0 bound ports).
        String podIp = resolveTestcontainersHostIp();
        LOG.info("Using podIp={} (host.testcontainers.internal as seen from k3s container)", podIp);

        var args = new CliArgs();
        new CommandLine(args).parseArgs(
                "--pod-ip",                podIp,
                "--proxy-port",            String.valueOf(proxyPort),
                "--traefik-metrics-url",   ext.traefikMetricsUrl(),
                "--idle-timeout",          idleTimeout,
                "--metrics-poll-interval", pollInterval,
                "--scale-up-timeout",      "30s",
                "--disable-legacy-endpoints"
                //"--propagation-delay",     "1200ms" // keep default propagation delay
        );

        scope = BeanScope.builder()
                .beans(args)
                .bean(KubernetesConfig.class, new KubernetesConfig.Raw(ext.kubeConfigYaml()))
                .build();

        LOG.info("DoormanSystemHarness started — proxy port={}, podIp={}", proxyPort, podIp);
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (scope != null) {
            scope.close();
            LOG.info("DoormanSystemHarness stopped");
        }
    }

    /** The port that {@code ProxyServer} is listening on. */
    public int proxyPort() {
        return proxyPort;
    }

    /** The running {@link BeanScope} — use to access singletons for assertions. */
    public BeanScope scope() {
        return scope;
    }

    // -------------------------------------------------------------------------
    // Test resource helpers
    // -------------------------------------------------------------------------

    /**
     * Deploys {@code mendhak/http-https-echo:latest} as a Deployment + Service in the given
     * namespace. The service exposes port 80 → targetPort 8080.
     */
    public void deployEchoApp(String namespace, String name) {
        var client = ext.client();

        client.apps().deployments().inNamespace(namespace).resource(
                new DeploymentBuilder()
                        .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                        .withNewSpec()
                            .withReplicas(1)
                            .withNewSelector().withMatchLabels(Map.of("app", name)).endSelector()
                            .withNewTemplate()
                                .withNewMetadata().withLabels(Map.of("app", name)).endMetadata()
                                .withNewSpec()
                                    .addNewContainer()
                                        .withName(name)
                                        .withImage("mendhak/http-https-echo:latest")
                                        .addNewPort().withContainerPort(8080).endPort()
                                    .endContainer()
                                .endSpec()
                            .endTemplate()
                        .endSpec()
                        .build()
        ).create();

        client.services().inNamespace(namespace).resource(
                new ServiceBuilder()
                        .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                        .withNewSpec()
                            .withSelector(Map.of("app", name))
                            .addNewPort().withPort(80).withTargetPort(new IntOrString(8080)).endPort()
                        .endSpec()
                        .build()
        ).create();
    }

    /**
     * Creates a minimal Ingress routing all traffic for {@code host} to {@code serviceName:80}.
     */
    public void createIngress(String namespace, String host, String serviceName) {
        ext.client().network().v1().ingresses().inNamespace(namespace).resource(
                new IngressBuilder()
                        .withNewMetadata()
                            .withName(serviceName)
                            .withNamespace(namespace)
                            .addToAnnotations("kubernetes.io/ingress.class", "traefik")
                        .endMetadata()
                        .withNewSpec()
                            .addNewRule()
                                .withHost(host)
                                .withNewHttp()
                                    .addNewPath()
                                        .withPath("/")
                                        .withPathType("Prefix")
                                        .withNewBackend()
                                            .withNewService()
                                                .withName(serviceName)
                                                .withNewPort().withNumber(80).endPort()
                                            .endService()
                                        .endBackend()
                                    .endPath()
                                .endHttp()
                            .endRule()
                        .endSpec()
                        .build()
        ).create();
    }

    /**
     * Creates a headless Service (ClusterIP, no selector) and a matching Endpoints resource
     * pointing to the Doorman proxy running in the test JVM.
     *
     * <p>This lets Traefik route through a standard Kubernetes Service to the in-process proxy
     * without any DNS resolution of external hostnames — the endpoint IP ({@link DoormanConfig#podIp()})
     * is the Docker host-gateway IP already known to the cluster.
     *
     * <p>Pair with {@link #createIngress(String, String, String)} to make requests routable
     * via Traefik from the test JVM using a Host header.
     */
    public void createProxyService(String namespace, String name) {
        var client = ext.client();
        var config = scope.get(DoormanConfig.class);

        client.services().inNamespace(namespace).resource(
                new ServiceBuilder()
                        .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                        .withNewSpec()
                            .withClusterIP("None")   // headless — kube-proxy doesn't intercept; Traefik resolves endpoints directly
                            .addNewPort().withPort(80).withTargetPort(new IntOrString(config.proxyPort())).endPort()
                        .endSpec()
                        .build()
        ).create();

        client.endpoints().inNamespace(namespace).resource(
                new EndpointsBuilder()
                        .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                        .withSubsets(new EndpointSubsetBuilder()
                                .withAddresses(new EndpointAddressBuilder().withIp(config.podIp()).build())
                                .withPorts(new EndpointPortBuilder()
                                        .withPort(config.proxyPort()).withProtocol("TCP").build())
                                .build())
                        .build()
        ).create();
    }

    /** Creates a {@code ScalingPolicy} custom resource. */
    public void createScalingPolicy(String namespace, String name, String serviceName,
                                    String deploymentName, String ingressName) {
        var policy = new ScalingPolicy();
        policy.setMetadata(new io.fabric8.kubernetes.api.model.ObjectMetaBuilder()
                .withName(name).withNamespace(namespace).build());
        var spec = new ScalingPolicySpec();
        spec.setServiceName(serviceName);
        spec.setDeploymentName(deploymentName);
        spec.setIngressName(ingressName);
        policy.setSpec(spec);
        ext.client().resources(ScalingPolicy.class).inNamespace(namespace).resource(policy).create();
    }

    /**
     * Waits until at least one pod matching {@code labelSelector} in {@code namespace} is Ready,
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitPodReady(String namespace, String labelSelector, int timeoutSeconds)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            var pods = ext.client().pods().inNamespace(namespace)
                    .withLabelSelector(labelSelector).list().getItems();
            boolean ready = pods.stream().anyMatch(p -> {
                var conditions = p.getStatus() == null ? null : p.getStatus().getConditions();
                if (conditions == null) return false;
                return conditions.stream().anyMatch(
                        c -> "Ready".equals(c.getType()) && "True".equals(c.getStatus()));
            });
            if (ready) return;
            Thread.sleep(2_000);
        }
        throw new AssertionError("No pod matching '" + labelSelector + "' in namespace '"
                + namespace + "' became Ready within " + timeoutSeconds + "s");
    }

    /**
     * Waits until the named {@code ScalingPolicy} reaches {@code expectedPhase},
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitScalingPolicyPhase(String namespace, String name,
                                        ScalingPolicyPhase expectedPhase, int timeoutSeconds)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            var policy = ext.client().resources(ScalingPolicy.class)
                    .inNamespace(namespace).withName(name).get();
            ScalingPolicyStatus status = policy == null ? null : policy.getStatus();
            if (status != null && expectedPhase.equals(status.getPhase())) return;
            Thread.sleep(1_000);
        }
        throw new AssertionError("ScalingPolicy '" + name + "' did not reach phase "
                + expectedPhase + " within " + timeoutSeconds + "s");
    }

    /** Returns the current {@code spec.replicas} for the named Deployment. */
    public int getCurrentReplicas(String namespace, String deploymentName) {
        var dep = ext.client().apps().deployments().inNamespace(namespace)
                .withName(deploymentName).get();
        if (dep == null || dep.getSpec() == null) return -1;
        Integer r = dep.getSpec().getReplicas();
        return r == null ? 1 : r;
    }

    /**
     * Waits until {@code spec.replicas} for the named Deployment equals {@code expected},
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitDeploymentReplicas(String namespace, String deploymentName,
                                        int expected, int timeoutSeconds) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            if (getCurrentReplicas(namespace, deploymentName) == expected) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Deployment '" + deploymentName + "' spec.replicas did not reach "
                + expected + " within " + timeoutSeconds + "s");
    }

    // -------------------------------------------------------------------------

    private static int findFreePort() throws Exception {
        try (var s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }

    /**
     * Resolves the IP address of {@code host.testcontainers.internal} from inside the k3s container.
     * This is the address that is actually reachable from within the container — on Mac Docker Desktop
     * this differs from the Docker bridge gateway (172.17.0.1), which points into the Linux VM
     * rather than the Mac host.
     */
    private String resolveTestcontainersHostIp() throws Exception {
        var result = ext.execInContainer("grep", "host.testcontainers.internal", "/etc/hosts");
        String line = result.getStdout().trim();
        if (line.isEmpty()) {
            throw new IllegalStateException(
                    "host.testcontainers.internal not found in k3s container /etc/hosts. " +
                    "Testcontainers may not have set up the host gateway entry.");
        }
        // /etc/hosts format: "IP hostname [alias...]"
        return line.split("\\s+")[0];
    }

    public void reconciliationSleep() throws Exception {
        Thread.sleep(scope.get(DoormanConfig.class).propagationDelay());
    }
}
