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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicyPhase;
import io.zeromagic.doorman.repository.crd.ScalingPolicyStatus;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;

@Singleton
class KubernetesScalingPolicyStatusPatcher implements ScalingPolicyStatusPatcher {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesScalingPolicyStatusPatcher.class);

    private final KubernetesClient client;

    KubernetesScalingPolicyStatusPatcher(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void patch(String namespace, String name, ScalingPolicyPhase phase,
                      Integer targetReplicas, String message) {
        try {
            var policy = new ScalingPolicy();
            policy.setMetadata(new ObjectMetaBuilder()
                    .withNamespace(namespace)
                    .withName(name)
                    .build());
            var status = new ScalingPolicyStatus();
            status.setPhase(phase);
            status.setLastTransitionTime(OffsetDateTime.now());
            status.setTargetReplicas(targetReplicas);
            status.setMessage(message);
            policy.setStatus(status);
            client.resource(policy).updateStatus();
        } catch (Exception e) {
            LOG.warn("Failed to patch status for ScalingPolicy {}/{}: {}", namespace, name, e.getMessage());
        }
    }
}
