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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.DoormanClusterSetup;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.KubernetesClientFacadeAccessor;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyStatus;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.Map;

/**
 * JUnit 5 extension that starts an isolated K3s cluster via Testcontainers and
 * prepares it with the Doorman CRD and a test namespace.
 *
 * <p>The container is started once per test class ({@code @RegisterExtension static}).
 * CRD and namespace are applied by {@link DoormanClusterSetup} and cleaned up after the class.
 *
 * <p>Usage:
 * <pre>{@code
 * @RegisterExtension
 * static final K3sClusterExtension K3S = new K3sClusterExtension("my-namespace");
 * }</pre>
 *
 * The {@link #facade()} method returns a {@link KubernetesFacade} configured for the k3s cluster,
 * suitable for constructing production components under test.
 */
public class K3sClusterExtension implements BeforeAllCallback, AfterAllCallback {

    static final DockerImageName K3S_IMAGE = DockerImageName.parse("rancher/k3s:v1.35.2-k3s1");

    private final String namespace;

    private K3sContainer container;
    private KubernetesClient client;
    private DoormanClusterSetup setup;

    public K3sClusterExtension(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        container = new K3sContainer(K3S_IMAGE);
        configureContainer(container);
        // Make the Docker host reachable from inside the container as host.testcontainers.internal.
        // Docker resolves "host-gateway" to the appropriate host IP on each platform
        // (Linux: docker bridge gateway; Mac/Windows: VM gateway that reaches the real host).
        // Required by DoormanSystemHarness for podIp and endpoint reachability probes.
        container.withExtraHost("host.testcontainers.internal", "host-gateway");
        container.start();

        client = new KubernetesClientBuilder()
                .withConfig(io.fabric8.kubernetes.client.Config.fromKubeconfig(container.getKubeConfigYaml()))
                .build();

        setup = new DoormanClusterSetup(client, namespace);
        setup.setup();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        try {
            if (setup != null) setup.teardown();
        } finally {
            if (client != null) client.close();
            if (container != null) container.stop();
        }
    }

    /**
     * Hook for subclasses to configure the container before it starts.
     * For example, subclasses may expose additional ports or copy files into the container.
     * The default implementation is a no-op.
     */
    protected void configureContainer(K3sContainer container) {}

    /** Returns the underlying K3s container. For use by subclasses that need container-level operations. */
    protected K3sContainer getK3sContainer() {
        return container;
    }

    /**
     * Returns the host port mapped from the given container port.
     * Only valid after the container has started.
     */
    protected int getMappedPort(int containerPort) {
        return container.getMappedPort(containerPort);
    }

    /** The Kubernetes client connected to the k3s cluster. */
    public KubernetesClient client() {
        return client;
    }

    /** The namespace managed by this extension. */
    public String namespace() {
        return namespace;
    }

    /**
     * A {@link KubernetesFacade} configured for the k3s cluster.
     * Use this to construct production components under test.
     */
    public KubernetesFacade facade() {
        return KubernetesClientFacadeAccessor.createFromKubeConfigYaml(container.getKubeConfigYaml());
    }

    /**
     * A {@link KubernetesFacade} configured for the k3s cluster with a specific {@link DoormanConfig}.
     * Use this when endpoint registration behaviour is under test.
     */
    public KubernetesFacade facade(io.zeromagic.doorman.cli.DoormanConfig doormanConfig) {
        return KubernetesClientFacadeAccessor.createFromKubeConfigYaml(container.getKubeConfigYaml(), doormanConfig);
    }

    /** The raw kubeconfig YAML for this k3s cluster. Use with {@code KubernetesConfig.Raw} to wire avaje in tests. */
    public String kubeConfigYaml() {
        return container.getKubeConfigYaml();
    }

    /**
     * The Docker bridge gateway IP — the IP address of the test JVM's host as seen from inside
     * the k3s container. Use as {@code podIp} when registering Doorman as a service endpoint so
     * that Traefik (inside k3s) can route requests back to the test JVM's proxy port.
     */
    public String containerGatewayIp() {
        return container.getContainerInfo().getNetworkSettings().getGateway();
    }

    /**
     * Executes a command inside the k3s container. Useful for connectivity probes in tests.
     */
    public org.testcontainers.containers.Container.ExecResult execInContainer(String... cmd)
            throws Exception {
        return container.execInContainer(cmd);
    }

    // -------------------------------------------------------------------------
    // Cluster resource helpers — shared by in-process and deployed harnesses
    // -------------------------------------------------------------------------

    /**
     * Deploys {@code mendhak/http-https-echo:latest} as a Deployment + Service in the given namespace.
     * The service exposes port 80 → targetPort 8080.
     */
    public void deployEchoApp(String namespace, String name) {
        client().apps().deployments().inNamespace(namespace).resource(
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

        client().services().inNamespace(namespace).resource(
                new ServiceBuilder()
                        .withNewMetadata().withName(name).withNamespace(namespace).endMetadata()
                        .withNewSpec()
                            .withSelector(Map.of("app", name))
                            .addNewPort().withPort(80).withTargetPort(new IntOrString(8080)).endPort()
                        .endSpec()
                        .build()
        ).create();
    }

    /** Creates a minimal Ingress routing all traffic for {@code host} to {@code serviceName:80}. */
    public void createIngress(String namespace, String host, String serviceName) {
        client().network().v1().ingresses().inNamespace(namespace).resource(
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

    /** Creates a {@link ScalingPolicy} custom resource. */
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
        client().resources(ScalingPolicy.class).inNamespace(namespace).resource(policy).create();
    }

    /**
     * Waits until at least one pod matching {@code labelSelector} in {@code namespace} is Ready,
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitPodReady(String namespace, String labelSelector, int timeoutSeconds)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            var pods = client().pods().inNamespace(namespace)
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
     * Waits until the named {@link ScalingPolicy} reaches {@code expectedPhase},
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitScalingPolicyPhase(String namespace, String name,
                                        ScalingPolicyPhase expectedPhase, int timeoutSeconds)
            throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            var policy = client().resources(ScalingPolicy.class)
                    .inNamespace(namespace).withName(name).get();
            ScalingPolicyStatus status = policy == null ? null : policy.getStatus();
            if (status != null && expectedPhase.equals(status.getPhase())) return;
            Thread.sleep(1_000);
        }
        throw new AssertionError("ScalingPolicy '" + name + "' did not reach phase "
                + expectedPhase + " within " + timeoutSeconds + "s");
    }

    /**
     * Waits until {@code spec.replicas} for the named Deployment equals {@code expected},
     * or throws after {@code timeoutSeconds}.
     */
    public void awaitDeploymentReplicas(String namespace, String deploymentName,
                                        int expected, int timeoutSeconds) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(timeoutSeconds);
        while (Instant.now().isBefore(deadline)) {
            var dep = client().apps().deployments().inNamespace(namespace)
                    .withName(deploymentName).get();
            int actual = (dep == null || dep.getSpec() == null || dep.getSpec().getReplicas() == null)
                    ? 1 : dep.getSpec().getReplicas();
            if (actual == expected) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Deployment '" + deploymentName + "' spec.replicas did not reach "
                + expected + " within " + timeoutSeconds + "s");
    }
}
