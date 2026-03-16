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
package io.zeromagic.doorman.traffic;

import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.ManualInClusterExtension;
import io.zeromagic.doorman.kubernetes.KubernetesClientFacadeAccessor;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual integration test for {@link TraefikServiceNameResolver} against a real cluster.
 * <p>
 * Run with: {@code mvn -Dtest=TraefikServiceNameResolverTestManual test}
 * <p>
 * Prerequisites: a reachable cluster (default kubeconfig).
 * The CRD is applied automatically from {@code deploy/scalingpolicy-crd.yaml} if absent.
 */
class TraefikServiceNameResolverTestManual {

    private static final String SERVICE = "inttest-svc";
    private static final String INGRESS_NAME = "inttest-ingress";
    private static final int PORT = 8080;
    private static final int PORT_V2 = 9090;
    private static final String INGRESS_NAME_V2 = "inttest-ingress-v2";

    @RegisterExtension
    static final ManualInClusterExtension CLUSTER = new ManualInClusterExtension("doorman-inttest");

    @Test
    void resolverIntegration() {
        var client = CLUSTER.client();
        var ns = CLUSTER.namespace();

        createIngress(ns, INGRESS_NAME, SERVICE, PORT);
        var policy = createScalingPolicy(ns, INGRESS_NAME);
        try {
            var facade = KubernetesClientFacadeAccessor.create();
            var resolver = new TraefikServiceNameResolver(facade);

            // AC1 + AC2: resolve Traefik label from Ingress
            resolver.onPolicyAdded(policy);
            assertThat(resolver.resolve(ns, SERVICE))
                    .as("initial resolution")
                    .contains(ns + "-" + SERVICE + "-" + PORT + "@kubernetes");

            // AC3: result is cached — remove ingress, result must still be present
            client.network().v1().ingresses().inNamespace(ns).withName(INGRESS_NAME).delete();
            assertThat(resolver.resolve(ns, SERVICE))
                    .as("cached after ingress deletion")
                    .contains(ns + "-" + SERVICE + "-" + PORT + "@kubernetes");

            // AC4: cache invalidated on policy update (new ingress with different port)
            createIngress(ns, INGRESS_NAME_V2, SERVICE, PORT_V2);
            var updatedPolicy = updateScalingPolicy(ns, policy, INGRESS_NAME_V2);
            resolver.onPolicyUpdated(policy, updatedPolicy);
            assertThat(resolver.resolve(ns, SERVICE))
                    .as("resolution after policy update")
                    .contains(ns + "-" + SERVICE + "-" + PORT_V2 + "@kubernetes");

            // AC5: returns empty when ingress is missing after cache invalidation
            client.network().v1().ingresses().inNamespace(ns).withName(INGRESS_NAME_V2).delete();
            resolver.onPolicyUpdated(updatedPolicy, updatedPolicy);
            assertThat(resolver.resolve(ns, SERVICE))
                    .as("empty when ingress missing")
                    .isEmpty();

        } finally {
            try { client.resources(ScalingPolicy.class).inNamespace(ns).withName(policy.getMetadata().getName()).delete(); } catch (Exception ignored) {}
            try { client.network().v1().ingresses().inNamespace(ns).withName(INGRESS_NAME).delete(); } catch (Exception ignored) {}
            try { client.network().v1().ingresses().inNamespace(ns).withName(INGRESS_NAME_V2).delete(); } catch (Exception ignored) {}
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void createIngress(String ns, String ingressName, String serviceName, int port) {
        var ingress = new IngressBuilder()
                .withNewMetadata().withNamespace(ns).withName(ingressName).endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withNewHttp()
                            .addNewPath()
                                .withPath("/")
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(serviceName)
                                        .withNewPort().withNumber(port).endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
        CLUSTER.client().network().v1().ingresses().inNamespace(ns).resource(ingress).createOrReplace();
    }

    private ScalingPolicy createScalingPolicy(String ns, String ingressName) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(SERVICE);
        spec.setDeploymentName("inttest-deployment");
        spec.setIngressName(ingressName);

        var policy = new ScalingPolicy();
        policy.getMetadata().setNamespace(ns);
        policy.getMetadata().setName("inttest-policy");
        policy.setSpec(spec);

        return CLUSTER.client().resources(ScalingPolicy.class).inNamespace(ns).resource(policy).createOrReplace();
    }

    private ScalingPolicy updateScalingPolicy(String ns, ScalingPolicy existing, String newIngressName) {
        existing.getSpec().setIngressName(newIngressName);
        return CLUSTER.client().resources(ScalingPolicy.class).inNamespace(ns).resource(existing).update();
    }
}
