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

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicySpec;
import org.junit.jupiter.api.Test;

import java.io.File;

class ScalingPolicyCRDTestManual {

    @Test
    void ensureScalingPolicyCrdAndResourceManual() throws Exception {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            var namespaceName = "0000-0000";
            boolean createdCrd = false;
            boolean createdNs = false;
            ScalingPolicy createdPolicy = null;

            var crdClient = client.apiextensions().v1();

            try {
                var existing = crdClient.customResourceDefinitions()
                    .withName("scalingpolicies.doorman.zeromagic.io")
                    .get();

                if (existing == null) {
                    File crdYaml = new File("deploy/scalingpolicy-crd.yaml");
                    crdClient.customResourceDefinitions().load(crdYaml).create();
                    createdCrd = true;
                }

                Namespace namespace = client.namespaces().withName(namespaceName).get();
                if (namespace == null) {
                    namespace = client.namespaces().create(new NamespaceBuilder()
                        .withNewMetadata().withName(namespaceName).endMetadata().build());
                    createdNs = true;
                }

                ScalingPolicy policy = new ScalingPolicy();
                ObjectMeta meta = new ObjectMetaBuilder()
                    .withName("manual-scalingpolicy")
                    .withNamespace(namespaceName)
                    .build();
                policy.setMetadata(meta);
                ScalingPolicySpec spec = new ScalingPolicySpec();
                spec.setServiceName("manual-service");
                spec.setDeploymentName("manual-deployment");
                policy.setSpec(spec);

                createdPolicy = client.resources(ScalingPolicy.class)
                    .inNamespace(namespaceName)
                    .create(policy);
            } finally {
                if (createdPolicy != null) {
                    client.resources(ScalingPolicy.class)
                        .inNamespace("0000-0000")
                        .withName("manual-scalingpolicy")
                        .delete();
                }
                if (createdNs) {
                    client.namespaces().withName(namespaceName).delete();
                }
                if (createdCrd) {
                    crdClient.customResourceDefinitions()
                        .withName("scalingpolicies.doorman.zeromagic.io")
                        .delete();
                }
            }
        }
    }
}
