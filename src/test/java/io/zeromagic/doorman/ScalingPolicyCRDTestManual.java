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

import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class ScalingPolicyCRDTestManual {

    @RegisterExtension
    static final DoormanClusterExtension CLUSTER = new DoormanClusterExtension("0000-0000");

    @Test
    void ensureScalingPolicyCrdAndResourceManual() throws Exception {
        var client = CLUSTER.client();
        var ns = CLUSTER.namespace();

        ScalingPolicy policy = new ScalingPolicy();
        policy.setMetadata(new ObjectMetaBuilder()
                .withName("manual-scalingpolicy")
                .withNamespace(ns)
                .build());
        ScalingPolicySpec spec = new ScalingPolicySpec();
        spec.setServiceName("manual-service");
        spec.setDeploymentName("manual-deployment");
        spec.setIngressName("manual-ingress");
        policy.setSpec(spec);

        var created = client.resources(ScalingPolicy.class).inNamespace(ns).create(policy);
        try {
            spec.setIdleTimeout("10m");
            client.resource(created).update();
        } finally {
            client.resources(ScalingPolicy.class).inNamespace(ns)
                    .withName(created.getMetadata().getName()).delete();
        }
    }
}
