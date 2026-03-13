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
import io.zeromagic.doorman.DoormanClusterSetup;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.KubernetesClientFacadeAccessor;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.k3s.K3sContainer;
import org.testcontainers.utility.DockerImageName;

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

    static final DockerImageName K3S_IMAGE = DockerImageName.parse("rancher/k3s:v1.31.5-k3s1");

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
}
