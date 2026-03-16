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
package io.zeromagic.doorman.proxy;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class IngressRouteIndexTest {

    private static final String NS           = "default";
    private static final String SERVICE      = "my-svc";
    private static final String INGRESS_NAME = "my-ingress";
    private static final String HOST         = "foo.example.com";

    private TestKubernetesFacade facade;
    private IngressRouteIndex index;

    @BeforeEach
    void setUp() {
        facade = new TestKubernetesFacade();
        index  = new IngressRouteIndex(facade);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private ScalingPolicy policy(String ns, String svc, String ingressName) {
        var spec = new ScalingPolicySpec();
        spec.setServiceName(svc);
        spec.setIngressName(ingressName);
        var policy = new ScalingPolicy();
        policy.getMetadata().setNamespace(ns);
        policy.getMetadata().setName(svc + "-policy");
        policy.setSpec(spec);
        return policy;
    }

    private Ingress ingress(String ns, String name, String host, String path, String serviceName) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(ns).withName(name).endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(host)
                        .withNewHttp()
                            .addNewPath()
                                .withPath(path)
                                .withPathType("Prefix")
                                .withNewBackend()
                                    .withNewService()
                                        .withName(serviceName)
                                        .withNewPort().withNumber(80).endPort()
                                    .endService()
                                .endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
    }

    private Ingress ingressTwoPaths(String ns, String name, String host,
                                    String path1, String path2, String serviceName) {
        return new IngressBuilder()
                .withNewMetadata().withNamespace(ns).withName(name).endMetadata()
                .withNewSpec()
                    .addNewRule()
                        .withHost(host)
                        .withNewHttp()
                            .addNewPath()
                                .withPath(path1).withPathType("Prefix")
                                .withNewBackend().withNewService()
                                    .withName(serviceName)
                                    .withNewPort().withNumber(80).endPort()
                                .endService().endBackend()
                            .endPath()
                            .addNewPath()
                                .withPath(path2).withPathType("Prefix")
                                .withNewBackend().withNewService()
                                    .withName(serviceName)
                                    .withNewPort().withNumber(80).endPort()
                                .endService().endBackend()
                            .endPath()
                        .endHttp()
                    .endRule()
                .endSpec()
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void resolve_basicMatch() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/api", SERVICE));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        Optional<RouteTarget> result = index.resolve(HOST, "/api/users");

        assertThat(result).contains(new RouteTarget(NS, SERVICE));
    }

    @Test
    void resolve_longestPrefixWins() {
        facade.stubIngress(ingressTwoPaths(NS, INGRESS_NAME, HOST, "/api", "/api/v2", SERVICE));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve(HOST, "/api/v2/users")).contains(new RouteTarget(NS, SERVICE));
        assertThat(index.resolve(HOST, "/api/v1/users")).contains(new RouteTarget(NS, SERVICE));
    }

    @Test
    void resolve_unknownHost_returnsEmpty() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/", SERVICE));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve("other.example.com", "/")).isEmpty();
    }

    @Test
    void resolve_pathNotPrefixed_returnsEmpty() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/api", SERVICE));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve(HOST, "/other")).isEmpty();
    }

    @Test
    void resolve_onDeleted_removesEntries() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/api", SERVICE));
        var p = policy(NS, SERVICE, INGRESS_NAME);
        index.onPolicyAdded(p);
        assertThat(index.resolve(HOST, "/api/x")).isPresent();

        index.onPolicyDeleted(p);

        assertThat(index.resolve(HOST, "/api/x")).isEmpty();
    }

    @Test
    void resolve_onPolicyUpdated_replacesEntries() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/old", SERVICE));
        var oldPolicy = policy(NS, SERVICE, INGRESS_NAME);
        index.onPolicyAdded(oldPolicy);
        assertThat(index.resolve(HOST, "/old/x")).isPresent();

        String newIngressName = "new-ingress";
        facade.stubIngress(ingress(NS, newIngressName, HOST, "/new", SERVICE));
        index.onPolicyUpdated(oldPolicy, policy(NS, SERVICE, newIngressName));

        assertThat(index.resolve(HOST, "/old/x")).isEmpty();
        assertThat(index.resolve(HOST, "/new/x")).contains(new RouteTarget(NS, SERVICE));
    }

    @Test
    void resolve_hostWithPort_stripsPort() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/api", SERVICE));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve(HOST + ":8080", "/api/data")).contains(new RouteTarget(NS, SERVICE));
    }

    @Test
    void resolve_noIngressName_skipsGracefully() {
        index.onPolicyAdded(policy(NS, SERVICE, ""));

        assertThat(index.resolve(HOST, "/")).isEmpty();
    }

    @Test
    void resolve_ingressNotFound_returnsEmpty() {
        // No ingress stubbed — facade.getIngress() returns empty
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve(HOST, "/")).isEmpty();
    }

    @Test
    void resolve_serviceNotInIngress_returnsEmpty() {
        facade.stubIngress(ingress(NS, INGRESS_NAME, HOST, "/api", "other-svc"));
        index.onPolicyAdded(policy(NS, SERVICE, INGRESS_NAME));

        assertThat(index.resolve(HOST, "/api/x")).isEmpty();
    }
}
