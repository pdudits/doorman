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

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import io.zeromagic.doorman.repository.crd.ScalingPolicySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TraefikServiceNameResolverTest {

    private static final String NS = "default";
    private static final String SERVICE = "my-svc";
    private static final String INGRESS_NAME = "my-ingress";

    private TestKubernetesFacade facade;
    private TraefikServiceNameResolver resolver;

    @BeforeEach
    void setUp() {
        facade = new TestKubernetesFacade();
        resolver = new TraefikServiceNameResolver(facade);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScalingPolicy policy(String namespace, String serviceName, String ingressName) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(serviceName);
        spec.setIngressName(ingressName);

        var policy = new ScalingPolicy();
        policy.getMetadata().setNamespace(namespace);
        policy.getMetadata().setName(serviceName + "-policy");
        policy.setSpec(spec);
        return policy;
    }

    private Ingress ingressWithRule(String namespace, String name, String serviceName, int port) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
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
    }

    private Ingress ingressWithDefaultBackend(String namespace, String name, String serviceName, int port) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(namespace).withName(name).endMetadata()
                .withNewSpec()
                    .withNewDefaultBackend()
                        .withNewService()
                            .withName(serviceName)
                            .withNewPort().withNumber(port).endPort()
                        .endService()
                    .endDefaultBackend()
                .endSpec()
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void resolve_happyPath_ingressRule() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<String> result = resolver.resolve(NS, SERVICE);

        assertThat(result).contains("default-my-svc-8080@kubernetes");
    }

    @Test
    void resolve_happyPath_defaultBackend() {
        facade.stubIngress(ingressWithDefaultBackend(NS, INGRESS_NAME, SERVICE, 3000));
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<String> result = resolver.resolve(NS, SERVICE);

        assertThat(result).contains("default-my-svc-3000@kubernetes");
    }

    @Test
    void resolve_ingressNotFound_returnsEmpty() {
        // No ingress stubbed
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<String> result = resolver.resolve(NS, SERVICE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_serviceNotInIngress_returnsEmpty() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, "other-svc", 8080));
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<String> result = resolver.resolve(NS, SERVICE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_noPolicyLoaded_returnsEmpty() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        // No onAdded called

        Optional<String> result = resolver.resolve(NS, SERVICE);

        assertThat(result).isEmpty();
    }

    @Test
    void resolve_resultIsCached_ingressFetchedOnce() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<String> first = resolver.resolve(NS, SERVICE);
        // Remove the ingress — if cached, second call still returns the label
        facade.removeIngress(NS, INGRESS_NAME);
        Optional<String> second = resolver.resolve(NS, SERVICE);

        assertThat(first).isEqualTo(second).contains("default-my-svc-8080@kubernetes");
    }

    @Test
    void resolve_cacheInvalidatedOnPolicyUpdate() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));
        Optional<String> before = resolver.resolve(NS, SERVICE);
        assertThat(before).contains("default-my-svc-8080@kubernetes");

        // Update: new ingress with different port
        String newIngressName = "new-ingress";
        facade.stubIngress(ingressWithRule(NS, newIngressName, SERVICE, 9090));
        resolver.onUpdated(
                policy(NS, SERVICE, INGRESS_NAME),
                policy(NS, SERVICE, newIngressName));

        Optional<String> after = resolver.resolve(NS, SERVICE);
        assertThat(after).contains("default-my-svc-9090@kubernetes");
    }

    @Test
    void resolve_onDeleted_clearsCache() {
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        var p = policy(NS, SERVICE, INGRESS_NAME);
        resolver.onAdded(p);
        resolver.resolve(NS, SERVICE); // populate cache

        resolver.onDeleted(p);
        facade.removeIngress(NS, INGRESS_NAME); // also remove ingress

        Optional<String> result = resolver.resolve(NS, SERVICE);
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_multipleServicesInDifferentNamespaces() {
        String ns2 = "other-ns";
        facade.stubIngress(ingressWithRule(NS, INGRESS_NAME, SERVICE, 8080));
        facade.stubIngress(ingressWithRule(ns2, INGRESS_NAME, SERVICE, 9090));

        resolver.onAdded(policy(NS, SERVICE, INGRESS_NAME));
        resolver.onAdded(policy(ns2, SERVICE, INGRESS_NAME));

        assertThat(resolver.resolve(NS, SERVICE)).contains("default-my-svc-8080@kubernetes");
        assertThat(resolver.resolve(ns2, SERVICE)).contains("other-ns-my-svc-9090@kubernetes");
    }
}
