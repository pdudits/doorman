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
package io.zeromagic.doorman;

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * JUnit 5 extension for manual integration tests against a real, pre-existing Kubernetes cluster.
 *
 * <p>Manages CRD and namespace lifecycle via {@link DoormanClusterSetup}.
 * Both are created if absent, and removed again after the test class if this extension created them.
 *
 * <p>Usage:
 * <pre>{@code
 * @RegisterExtension
 * static final ManualInClusterExtension CLUSTER = new ManualInClusterExtension("my-namespace");
 * }</pre>
 *
 * @see io.zeromagic.doorman.k3s.K3sClusterExtension for a fully isolated, container-based alternative
 */
public class ManualInClusterExtension implements BeforeAllCallback, AfterAllCallback {

    private final String namespace;

    private KubernetesClient client;
    private DoormanClusterSetup setup;

    public ManualInClusterExtension(String namespace) {
        this.namespace = namespace;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        client = new KubernetesClientBuilder().build();
        setup = new DoormanClusterSetup(client, namespace);
        setup.setup();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        try {
            setup.teardown();
        } finally {
            client.close();
        }
    }

    /** The shared Kubernetes client. Available between {@code beforeAll} and {@code afterAll}. */
    public KubernetesClient client() {
        return client;
    }

    /** The namespace managed by this extension. */
    public String namespace() {
        return namespace;
    }
}
