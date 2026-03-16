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

import io.zeromagic.doorman.cli.TraefikConfig;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

class FixedMetricsEndpointSourceTest {

    @Test
    void endpoints_returnsSingleConfiguredUri() {
        var config = new TraefikConfig.Direct("http://traefik:9100/metrics", "5m", "15s");
        var source = new FixedMetricsEndpointSource(config);

        assertThat(source.endpoints())
                .hasSize(1)
                .containsExactly(URI.create("http://traefik:9100/metrics"));
    }

    @Test
    void endpoints_isImmutable() {
        var source = new FixedMetricsEndpointSource(
                new TraefikConfig.Direct("http://traefik:9100/metrics", "5m", "15s"));

        assertThat(source.endpoints()).isUnmodifiable();
    }
}
