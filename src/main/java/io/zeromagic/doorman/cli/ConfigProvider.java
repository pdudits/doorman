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

import io.avaje.inject.Bean;
import io.avaje.inject.External;
import io.avaje.inject.Factory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@Factory
class ConfigProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConfigProvider.class);

    @Bean
    Optional<KubernetesConfig> kubernetesConfig(@External CliArgs args) {
        var ctx = args.kubeContext;
        return (ctx != null && !ctx.isBlank()) ? Optional.of(new KubernetesConfig.Context(ctx)) : Optional.empty();
    }

    @Bean
    DoormanConfig doormanConfig(@External CliArgs args) {
        String ip = args.podIp;
        if (ip == null || ip.isBlank()) {
            throw new IllegalStateException(
                    "Doorman pod IP is not configured. Set --pod-ip or expose POD_IP via the Downward API.");
        }
        var config = new DoormanConfig(ip, args.proxyPort, DurationParser.parse(args.scaleUpTimeout), DurationParser.parse(args.propagationDelay));
        LOGGER.info("Doorman config: podIp={}, proxyPort={}, scaleUpTimeout={}, propagationDelay={}",
                config.podIp(), config.proxyPort(), config.scaleUpTimeout(), config.propagationDelay());
        return config;
    }

    @Bean
    TraefikConfig traefikConfig(@External CliArgs args) {
        if (args.traefikMetricsUrl != null && !args.traefikMetricsUrl.isBlank()) {
            var config = new TraefikConfig.Direct(args.traefikMetricsUrl, args.idleTimeout, args.metricsPollInterval);
            LOGGER.info("Traefik config: direct url={}, idleTimeout={}, pollInterval={}",
                    args.traefikMetricsUrl, args.idleTimeout, args.metricsPollInterval);
            return config;
        }
        if (args.traefikNamespace != null && !args.traefikNamespace.isBlank()
                && args.traefikLabelSelector != null && !args.traefikLabelSelector.isBlank()) {
            var config = new TraefikConfig.Discovered(
                    args.traefikNamespace, args.traefikLabelSelector, args.traefikMetricsPort,
                    args.idleTimeout, args.metricsPollInterval);
            LOGGER.info("Traefik config: discovered namespace={}, selector={}, port={}, idleTimeout={}, pollInterval={}",
                    args.traefikNamespace, args.traefikLabelSelector, args.traefikMetricsPort,
                    args.idleTimeout, args.metricsPollInterval);
            return config;
        }
        throw new IllegalStateException(
                "Traefik metrics source is not configured. " +
                "Provide --traefik-metrics-url for single-endpoint mode, " +
                "or both --traefik-namespace and --traefik-label-selector for pod-discovery mode.");
    }
}
