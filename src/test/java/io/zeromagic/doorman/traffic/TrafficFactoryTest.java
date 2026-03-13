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

import io.avaje.inject.BeanScope;
import io.zeromagic.doorman.cli.CliArgs;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import static org.assertj.core.api.Assertions.assertThat;

class TrafficFactoryTest {

    private static final KubernetesFacade FACADE = new TestKubernetesFacade();

    private BeanScope scopeWith(String... cliArgStrings) {
        var args = new CliArgs();
        new CommandLine(args).parseArgs(cliArgStrings);
        return BeanScope.builder()
                .bean(CliArgs.class, args)
                .bean(KubernetesFacade.class, FACADE)
                .build();
    }

    @Test
    void withMetricsUrl_producesFixedEndpointSource() {
        try (var scope = scopeWith("--traefik-metrics-url", "http://traefik:9100/metrics", "--pod-ip", "10.0.0.1")) {
            assertThat(scope.get(MetricsEndpointSource.class))
                    .isInstanceOf(FixedMetricsEndpointSource.class);
        }
    }

    @Test
    void withNamespaceAndSelector_producesKubernetesPodEndpointSource() {
        try (var scope = scopeWith("--traefik-namespace", "kube-system", "--traefik-label-selector", "app=traefik", "--pod-ip", "10.0.0.1")) {
            assertThat(scope.get(MetricsEndpointSource.class))
                    .isInstanceOf(KubernetesPodMetricsEndpointSource.class);
        }
    }
}
