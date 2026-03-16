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
package io.zeromagic.doorman.cli;

import io.avaje.inject.BeanScope;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.TestKubernetesFacade;
import io.zeromagic.doorman.kubernetes.events.ScalingPolicyEvents;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class InjectionTest {
    @Test
    void avajeFiresUp() {
        var cliArgs = new CliArgs();
        cliArgs.podIp = "127.0.0.1";
        cliArgs.traefikMetricsUrl = "http://localhost:9001/metrics";
        cliArgs.idleTimeout = "60s";
        cliArgs.metricsPollInterval = "10s";
        var testKubernetesFacade = new TestKubernetesFacade();
        try (var scope = startModule()) {

        }
    }

    @Test
    void whatsSingularPolicyEvent() {
        try (var scope = startModule()) {
            var singularPolicyEventHandler = scope.get(ScalingPolicyEvents.class);
            // Avaje bug: this is not null and it doesn't throw. Be happy when this test fails
            Assertions.assertThat(singularPolicyEventHandler).isNotNull(); // It's ambiguous we shouldn't get singular instance
        }
    }

    @Test
    void whatIsInjectedAsSingularPolicyEvent() {
        try (var scope = startModule()) {
            var dummy = scope.get(DummyBean.class);
            // Avaje bug: This should have failed to start as that is an ambiguous dependency.
            Assertions.assertThat(dummy.getScalingPolicyEvents()).isNotNull();
        }
    }

    private BeanScope startModule() {
        var cliArgs = new CliArgs();
        cliArgs.podIp = "127.0.0.1";
        cliArgs.traefikMetricsUrl = "http://localhost:9001/metrics";
        cliArgs.idleTimeout = "60s";
        cliArgs.metricsPollInterval = "10s";
        var testKubernetesFacade = new TestKubernetesFacade();
        return BeanScope
                .builder()
                .beans(cliArgs)
                .bean(KubernetesFacade.class, testKubernetesFacade)
                .build();
    }
}
