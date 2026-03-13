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

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class KubernetesPodMetricsEndpointSourceTest {

    private static final TraefikConfig.Discovered CONFIG =
            new TraefikConfig.Discovered("kube-system", "app=traefik", 9100, "10m", "30s");

    private Pod pod(String name, String ip) {
        return new PodBuilder()
                .withNewMetadata().withName(name).endMetadata()
                .withNewStatus().withPodIP(ip).endStatus()
                .build();
    }

    @Test
    void endpoints_emptyOnStart() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);
        assertThat(source.endpoints()).isEmpty();
    }

    @Test
    void endpoints_updatesOnPodAdd() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);

        facade.fireAdd(pod("traefik-1", "10.0.0.1"));

        assertThat(source.endpoints())
                .containsExactly(URI.create("http://10.0.0.1:9100/metrics"));
    }

    @Test
    void endpoints_updatesOnPodUpdate() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);

        var old = pod("traefik-1", "10.0.0.1");
        var updated = pod("traefik-1", "10.0.0.2");
        facade.fireAdd(old);
        facade.fireUpdate(old, updated);

        assertThat(source.endpoints())
                .containsExactly(URI.create("http://10.0.0.2:9100/metrics"));
    }

    @Test
    void endpoints_removedOnPodDelete() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);

        var p = pod("traefik-1", "10.0.0.1");
        facade.fireAdd(p);
        facade.fireDelete(p);

        assertThat(source.endpoints()).isEmpty();
    }

    @Test
    void endpoints_multiplePodsTracked() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);

        facade.fireAdd(pod("traefik-1", "10.0.0.1"));
        facade.fireAdd(pod("traefik-2", "10.0.0.2"));

        assertThat(source.endpoints()).containsExactlyInAnyOrder(
                URI.create("http://10.0.0.1:9100/metrics"),
                URI.create("http://10.0.0.2:9100/metrics"));
    }

    @Test
    void endpoints_podWithNoIpIgnored() {
        var facade = new TestKubernetesFacade();
        var source = new KubernetesPodMetricsEndpointSource(CONFIG, facade);

        facade.fireAdd(new PodBuilder()
                .withNewMetadata().withName("traefik-pending").endMetadata()
                .build()); // no status/IP

        assertThat(source.endpoints()).isEmpty();
    }
}
