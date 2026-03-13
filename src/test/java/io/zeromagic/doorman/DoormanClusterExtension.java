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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * JUnit 5 extension for manual integration tests that need a real Kubernetes cluster.
 *
 * <p>Provides:
 * <ul>
 *   <li>A shared {@link KubernetesClient} for the duration of the test class.</li>
 *   <li>Idempotent CRD setup: applies {@code deploy/scalingpolicy-crd.yaml} if absent,
 *       and removes it again after the test class if it was applied by this extension.</li>
 *   <li>Idempotent namespace setup: creates the requested namespace if absent, and removes
 *       it again after the test class if it was created by this extension.</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * @ExtendWith(DoormanClusterExtension.class)
 * class MyTestManual {
 *     @Test void myTest(DoormanClusterExtension ext) { ... }
 * }
 * }</pre>
 *
 * Or store the extension as a static field:
 * <pre>{@code
 * @RegisterExtension
 * static final DoormanClusterExtension CLUSTER = new DoormanClusterExtension("my-namespace");
 * }</pre>
 */
public class DoormanClusterExtension implements BeforeAllCallback, AfterAllCallback {

    private static final Logger LOG = LoggerFactory.getLogger(DoormanClusterExtension.class);
    private static final String CRD_NAME = "scalingpolicies.doorman.zeromagic.io";
    private static final File CRD_YAML = new File("deploy/scalingpolicy-crd.yaml");

    private final String namespace;

    private KubernetesClient client;
    private boolean ownedCrd;
    private boolean ownedNamespace;

    public DoormanClusterExtension(String namespace) {
        this.namespace = namespace;
    }

    // ── BeforeAll / AfterAll ──────────────────────────────────────────────────

    @Override
    public void beforeAll(ExtensionContext context) {
        client = new KubernetesClientBuilder().build();
        ensureCrd();
        ensureNamespace();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        try {
            if (ownedNamespace) {
                LOG.info("Deleting namespace {}", namespace);
                client.namespaces().withName(namespace).delete();
            }
            if (ownedCrd) {
                LOG.info("Deleting CRD {}", CRD_NAME);
                client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME).delete();
            }
        } finally {
            client.close();
        }
    }

    // ── Public accessors ──────────────────────────────────────────────────────

    /** The shared Kubernetes client. Available between {@code beforeAll} and {@code afterAll}. */
    public KubernetesClient client() {
        return client;
    }

    /** The namespace managed by this extension. */
    public String namespace() {
        return namespace;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void ensureCrd() {
        var existing = client.apiextensions().v1().customResourceDefinitions()
                .withName(CRD_NAME).get();
        if (existing == null) {
            LOG.info("Applying CRD from {}", CRD_YAML);
            client.apiextensions().v1().customResourceDefinitions().load(CRD_YAML).create();
            ownedCrd = true;
        } else {
            LOG.debug("CRD {} already present", CRD_NAME);
        }
    }

    private void ensureNamespace() {
        if (client.namespaces().withName(namespace).get() == null) {
            LOG.info("Creating namespace {}", namespace);
            client.namespaces().resource(
                    new io.fabric8.kubernetes.api.model.NamespaceBuilder()
                            .withNewMetadata().withName(namespace).endMetadata()
                            .build()
            ).create();
            ownedNamespace = true;
        } else {
            LOG.debug("Namespace {} already present", namespace);
        }
    }
}
