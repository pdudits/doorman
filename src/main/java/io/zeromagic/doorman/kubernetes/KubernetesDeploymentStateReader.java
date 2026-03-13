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

package io.zeromagic.doorman.kubernetes;

import io.fabric8.kubernetes.client.KubernetesClient;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
class KubernetesDeploymentStateReader implements DeploymentStateReader {

    private final KubernetesClient client;

    KubernetesDeploymentStateReader(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public Optional<DeploymentState> read(String namespace, String deploymentName) {
        var deployment = client.apps().deployments()
                .inNamespace(namespace).withName(deploymentName).get();
        if (deployment == null) {
            return Optional.empty();
        }
        int spec = deployment.getSpec() != null && deployment.getSpec().getReplicas() != null
                ? deployment.getSpec().getReplicas() : 0;
        int ready = deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null
                ? deployment.getStatus().getReadyReplicas() : 0;
        return Optional.of(new DeploymentState(spec, ready));
    }
}
