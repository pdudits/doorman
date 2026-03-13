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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Idempotent setup and teardown of the Doorman CRD and a test namespace
 * against a given {@link KubernetesClient}.
 *
 * <p>Used by both {@link ManualInClusterExtension} (real cluster) and
 * {@code K3sClusterExtension} (Testcontainers) to share lifecycle logic.
 */
public class DoormanClusterSetup {

    private static final Logger LOG = LoggerFactory.getLogger(DoormanClusterSetup.class);
    static final String CRD_NAME = "scalingpolicies.doorman.zeromagic.io";
    private static final String CRD_RESOURCE = "scalingpolicy-crd.yaml";

    private final KubernetesClient client;
    private final String namespace;

    private boolean ownedCrd;
    private boolean ownedNamespace;

    public DoormanClusterSetup(KubernetesClient client, String namespace) {
        this.client = client;
        this.namespace = namespace;
    }

    public void setup() {
        ensureCrd();
        ensureNamespace();
    }

    public void teardown() {
        if (ownedNamespace) {
            LOG.info("Deleting namespace {}", namespace);
            client.namespaces().withName(namespace).delete();
        }
        if (ownedCrd) {
            LOG.info("Deleting CRD {}", CRD_NAME);
            client.apiextensions().v1().customResourceDefinitions().withName(CRD_NAME).delete();
        }
    }

    public String namespace() {
        return namespace;
    }

    private void ensureCrd() {
        var existing = client.apiextensions().v1().customResourceDefinitions()
                .withName(CRD_NAME).get();
        if (existing == null) {
            LOG.info("Applying CRD from classpath:{}", CRD_RESOURCE);
            try (InputStream is = DoormanClusterSetup.class.getClassLoader().getResourceAsStream(CRD_RESOURCE)) {
                if (is == null) throw new IllegalStateException("CRD resource not found on classpath: " + CRD_RESOURCE);
                client.apiextensions().v1().customResourceDefinitions().load(is).create();
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Failed to load CRD resource: " + CRD_RESOURCE, e);
            }
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
