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

import picocli.CommandLine;

@CommandLine.Command(name = "doorman", mixinStandardHelpOptions = true)
public class CliArgs {
    @CommandLine.Option(names = "--kube-context")
    String kubeContext;

    @CommandLine.Option(names = "--pod-ip",
            description = "IP address Doorman registers as a service endpoint. " +
                    "Defaults to POD_IP env var (Downward API). Required when running outside a cluster.")
    String podIp;

    @CommandLine.Option(names = "--traefik-metrics-url",
            description = "Single Traefik metrics endpoint URL (e.g. http://traefik:9100/metrics). " +
                    "Mutually exclusive with --traefik-namespace/--traefik-label-selector.")
    String traefikMetricsUrl;

    @CommandLine.Option(names = "--traefik-namespace",
            description = "Kubernetes namespace to discover Traefik pods for metrics scraping.")
    String traefikNamespace;

    @CommandLine.Option(names = "--traefik-label-selector",
            description = "Label selector for Traefik pods (e.g. 'app=traefik').")
    String traefikLabelSelector;

    @CommandLine.Option(names = "--traefik-metrics-port",
            description = "Port to scrape on each discovered Traefik pod (default: 9100).",
            defaultValue = "9100")
    int traefikMetricsPort;

    @CommandLine.Option(names = "--idle-timeout",
            description = "Global idle timeout before a service is scaled to zero (default: 5m).",
            defaultValue = "5m")
    String idleTimeout;

    @CommandLine.Option(names = "--metrics-poll-interval",
            description = "How often to poll Traefik metrics (default: 15s).",
            defaultValue = "15s")
    String metricsPollInterval;

    @CommandLine.Option(names = "--proxy-port",
            description = "Port that Doorman's HTTP proxy server listens on (default: 8080). " +
                    "This port is registered in EndpointSlice/Endpoints when intercepting scaled-down services.",
            defaultValue = "8080")
    int proxyPort;

    @CommandLine.Option(names = "--scale-up-timeout",
            description = "Maximum time to wait for a service to become ready before returning 503 (default: 60s).",
            defaultValue = "60s")
    String scaleUpTimeout;
}
