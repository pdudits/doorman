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

import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.ObjectFieldSelectorBuilder;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.dsl.LogWatch;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.utility.MountableFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Map;

/**
 * JUnit 5 extension that extends {@link TraefikK3sExtension} with a real Doorman pod running
 * inside k3s. Intended for blackbox end-to-end tests — no in-process Doorman components.
 *
 * <p>Before tests start, this extension:
 * <ol>
 *   <li>Imports the locally built Doorman Docker image into k3s's containerd runtime via
 *       {@code k3s ctr images import}. The image tar path is read from the system property
 *       {@code doorman.image.tar} (set by the {@code blackbox} Maven profile via docker:save).</li>
 *   <li>Applies the deployment manifests ({@code namespace.yaml}, {@code serviceaccount.yaml},
 *       {@code rbac.yaml}) from the classpath.</li>
 *   <li>Creates the Doorman Deployment with test-appropriate configuration:
 *       short idle timeout, Traefik discovery by label, {@code imagePullPolicy: Never}.</li>
 *   <li>Waits for the Doorman pod to be Ready in {@code doorman-system}.</li>
 *   <li>Streams Doorman pod logs to SLF4J for diagnostics.</li>
 * </ol>
 *
 * <p>Usage (always annotate the test class with {@code @Tag("blackbox")}):
 * <pre>{@code
 * @Tag("blackbox")
 * class MyBlackboxIT {
 *     @RegisterExtension
 *     static final DeployedDoormanK3sExtension K3S = new DeployedDoormanK3sExtension("my-ns", "10s");
 * }
 * }</pre>
 */
public class DeployedDoormanK3sExtension extends TraefikK3sExtension {

    private static final Logger LOG = LoggerFactory.getLogger(DeployedDoormanK3sExtension.class);
    private static final Logger DOORMAN_LOG = LoggerFactory.getLogger("doorman-pod");

    static final String DOORMAN_IMAGE = "docker.io/pdudits/doorman:latest";
    static final String DOORMAN_NAMESPACE = "doorman-system";

    private final String idleTimeout;

    public DeployedDoormanK3sExtension(String testNamespace, String idleTimeout) {
        super(testNamespace);
        this.idleTimeout = idleTimeout;
    }

    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        super.beforeAll(context);
        importDoormanImage();
        applyDeployManifests();
        createDoormanDeployment();
        awaitPodReady(DOORMAN_NAMESPACE, "app=doorman", 120);
        watchDoormanLogs();
    }

    // -------------------------------------------------------------------------

    private void importDoormanImage() throws Exception {
        String tarPath = System.getProperty("doorman.image.tar");
        if (tarPath == null || tarPath.isBlank()) {
            throw new IllegalStateException(
                    "System property 'doorman.image.tar' is not set. " +
                    "Run with the 'blackbox' Maven profile: mvn verify -P blackbox");
        }
        LOG.info("Copying doorman image tar {} into k3s container", tarPath);
        copyFileToContainer(MountableFile.forHostPath(Path.of(tarPath)), "/tmp/doorman.tar");
        var result = execInContainer("ctr", "--address=/run/k3s/containerd/containerd.sock",
                "images", "import", "/tmp/doorman.tar");
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                    "Failed to import doorman image: " + result.getStderr());
        }
        LOG.info("Doorman image imported: {}", result.getStdout().trim());
    }

    private void applyDeployManifests() {
        for (var manifest : new String[]{"namespace.yaml", "serviceaccount.yaml", "rbac.yaml"}) {
            try (var is = getClass().getClassLoader().getResourceAsStream(manifest)) {
                if (is == null) throw new IllegalStateException(manifest + " not found on classpath");
                client().load(is).create();
                LOG.info("Applied {}", manifest);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to apply " + manifest, e);
            }
        }
    }

    private void createDoormanDeployment() {
        String version = System.getProperty("doorman.version", "1.0-SNAPSHOT");
        client().apps().deployments().inNamespace(DOORMAN_NAMESPACE).resource(
                new DeploymentBuilder()
                        .withNewMetadata()
                            .withName("doorman")
                            .withNamespace(DOORMAN_NAMESPACE)
                        .endMetadata()
                        .withNewSpec()
                            .withReplicas(1)
                            .withNewSelector().withMatchLabels(Map.of("app", "doorman")).endSelector()
                            .withNewTemplate()
                                .withNewMetadata().withLabels(Map.of("app", "doorman")).endMetadata()
                                .withNewSpec()
                                    .withServiceAccountName("doorman")
                                    .addNewContainer()
                                        .withName("doorman")
                                        .withImage(DOORMAN_IMAGE)
                                        .withImagePullPolicy("Never")
                                        .withCommand("java", "-cp",
                                            "/app/lib/*:/app/doorman-" + version + ".jar",
                                            "io.zeromagic.doorman.Main")
                                        .withArgs(
                                            "--traefik-namespace=kube-system",
                                            "--traefik-label-selector=app.kubernetes.io/name=traefik",
                                            "--idle-timeout=" + idleTimeout,
                                            "--scale-up-timeout=30s",
                                            "--disable-legacy-endpoints"
                                        )
                                        .withEnv(new EnvVarBuilder()
                                                .withName("POD_IP")
                                                .withValueFrom(new EnvVarSourceBuilder()
                                                        .withFieldRef(new ObjectFieldSelectorBuilder()
                                                                .withFieldPath("status.podIP")
                                                                .build())
                                                        .build())
                                                .build())
                                        .addNewPort().withContainerPort(8080).endPort()
                                    .endContainer()
                                .endSpec()
                            .endTemplate()
                        .endSpec()
                        .build()
        ).create();
        LOG.info("Created Doorman deployment in {} (image: {}, version: {})",
                DOORMAN_NAMESPACE, DOORMAN_IMAGE, version);
    }

    private void watchDoormanLogs() {
        var pods = client().pods().inNamespace(DOORMAN_NAMESPACE)
                .withLabel("app", "doorman").list().getItems();
        pods.forEach(pod -> {
            var name = pod.getMetadata().getName();
            Thread.ofVirtual().name("log-" + name).start(() ->
                    pumpDoormanLog(name, client().pods().resource(pod).watchLog()));
        });
    }

    private void pumpDoormanLog(String podName, LogWatch logWatch) {
        try (var reader = new BufferedReader(new InputStreamReader(logWatch.getOutput()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                DOORMAN_LOG.info("[{}] {}", podName, line);
            }
        } catch (IOException e) {
            LOG.debug("Doorman log stream for {} ended", podName, e);
        }
    }

    /**
     * Copies a file from the host into the k3s container.
     */
    protected void copyFileToContainer(MountableFile file, String containerPath) throws Exception {
        getK3sContainer().copyFileToContainer(file, containerPath);
    }
}
