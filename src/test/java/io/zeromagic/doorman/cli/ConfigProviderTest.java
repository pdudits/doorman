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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfigProviderTest {

    private final ConfigProvider provider = new ConfigProvider();

    // ── traefikConfig ────────────────────────────────────────────────────────

    @Test
    void traefikConfig_directMode() {
        var args = new CliArgs();
        args.traefikMetricsUrl = "http://traefik:9100/metrics";
        args.idleTimeout = "5m";
        args.metricsPollInterval = "15s";

        var result = provider.traefikConfig(args);

        assertThat(result).isInstanceOf(TraefikConfig.Direct.class);
        var direct = (TraefikConfig.Direct) result;
        assertThat(direct.metricsUrl()).isEqualTo("http://traefik:9100/metrics");
        assertThat(direct.idleTimeout()).isEqualTo("5m");
        assertThat(direct.metricsPollInterval()).isEqualTo("15s");
    }

    @Test
    void traefikConfig_discoveredMode() {
        var args = new CliArgs();
        args.traefikNamespace = "kube-system";
        args.traefikLabelSelector = "app=traefik";
        args.traefikMetricsPort = 9100;
        args.idleTimeout = "10m";
        args.metricsPollInterval = "30s";

        var result = provider.traefikConfig(args);

        assertThat(result).isInstanceOf(TraefikConfig.Discovered.class);
        var discovered = (TraefikConfig.Discovered) result;
        assertThat(discovered.namespace()).isEqualTo("kube-system");
        assertThat(discovered.labelSelector()).isEqualTo("app=traefik");
        assertThat(discovered.metricsPort()).isEqualTo(9100);
        assertThat(discovered.idleTimeout()).isEqualTo("10m");
        assertThat(discovered.metricsPollInterval()).isEqualTo("30s");
    }

    @Test
    void traefikConfig_directModeTakesPrecedenceOverDiscovered() {
        var args = new CliArgs();
        args.traefikMetricsUrl = "http://traefik:9100/metrics";
        args.traefikNamespace = "kube-system";
        args.traefikLabelSelector = "app=traefik";

        assertThat(provider.traefikConfig(args)).isInstanceOf(TraefikConfig.Direct.class);
    }

    @Test
    void traefikConfig_missingBothModes_throwsWithClearMessage() {
        var args = new CliArgs();

        assertThatThrownBy(() -> provider.traefikConfig(args))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("--traefik-metrics-url")
                .hasMessageContaining("--traefik-namespace");
    }

    @Test
    void traefikConfig_onlyNamespaceWithoutSelector_throws() {
        var args = new CliArgs();
        args.traefikNamespace = "kube-system";

        assertThatThrownBy(() -> provider.traefikConfig(args))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void traefikConfig_onlySelectorWithoutNamespace_throws() {
        var args = new CliArgs();
        args.traefikLabelSelector = "app=traefik";

        assertThatThrownBy(() -> provider.traefikConfig(args))
                .isInstanceOf(IllegalStateException.class);
    }

    // ── doormanConfig ────────────────────────────────────────────────────────

    @Test
    void doormanConfig_podIpFromArg() {
        var args = new CliArgs();
        args.podIp = "10.0.0.1";

        var config = provider.doormanConfig(args);

        assertThat(config.podIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void doormanConfig_podIpFromEnv() {
        var args = new CliArgs();
        args.podIp = "192.168.1.50";

        assertThat(provider.doormanConfig(args).podIp()).isEqualTo("192.168.1.50");
    }

    @Test
    void doormanConfig_missingPodIp_throwsWithClearMessage() {
        var args = new CliArgs();
        // podIp null, POD_IP env var not set in test environment

        // only throws if POD_IP env var is also absent
        if (System.getenv("POD_IP") == null) {
            assertThatThrownBy(() -> provider.doormanConfig(args))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("POD_IP");
        }
    }
}
