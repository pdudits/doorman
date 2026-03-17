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
    @CommandLine.Option(names = "--kube-context",
            description = "Kubernetes context name to use (default: current context). Env: KUBE_CONTEXT.",
            defaultValue = "${KUBE_CONTEXT:-}")
    String kubeContext;

    @CommandLine.Option(names = "--pod-ip",
            description = "IP address Doorman registers as a service endpoint. " +
                    "Defaults to POD_IP env var (Downward API). Required when running outside a cluster.",
            defaultValue = "${POD_IP:-}")
    String podIp;

    @CommandLine.Option(names = "--traefik-metrics-url",
            description = "Single Traefik metrics endpoint URL (e.g. http://traefik:9100/metrics). " +
                    "Mutually exclusive with --traefik-namespace/--traefik-label-selector. Env: TRAEFIK_METRICS_URL.",
            defaultValue = "${TRAEFIK_METRICS_URL:-}")
    String traefikMetricsUrl;

    @CommandLine.Option(names = "--traefik-namespace",
            description = "Kubernetes namespace to discover Traefik pods for metrics scraping. Env: TRAEFIK_NAMESPACE.",
            defaultValue = "${TRAEFIK_NAMESPACE:-}")
    String traefikNamespace;

    @CommandLine.Option(names = "--traefik-label-selector",
            description = "Label selector for Traefik pods (e.g. 'app=traefik'). Env: TRAEFIK_LABEL_SELECTOR.",
            defaultValue = "${TRAEFIK_LABEL_SELECTOR:-}")
    String traefikLabelSelector;

    @CommandLine.Option(names = "--traefik-metrics-port",
            description = "Port to scrape on each discovered Traefik pod (default: 9100). Env: TRAEFIK_METRICS_PORT.",
            defaultValue = "${TRAEFIK_METRICS_PORT:-9100}")
    int traefikMetricsPort;

    @CommandLine.Option(names = "--idle-timeout",
            description = "Global idle timeout before a service is scaled to zero (default: 5m). Env: IDLE_TIMEOUT.",
            defaultValue = "${IDLE_TIMEOUT:-5m}")
    String idleTimeout;

    @CommandLine.Option(names = "--metrics-poll-interval",
            description = "How often to poll Traefik metrics (default: 15s). Env: METRICS_POLL_INTERVAL.",
            defaultValue = "${METRICS_POLL_INTERVAL:-15s}")
    String metricsPollInterval;

    @CommandLine.Option(names = "--proxy-port",
            description = "Port that Doorman's HTTP proxy server listens on (default: 8080). " +
                    "This port is registered in EndpointSlice/Endpoints when intercepting scaled-down services. Env: DOORMAN_PROXY_PORT.",
            defaultValue = "${DOORMAN_PROXY_PORT:-8080}")
    int proxyPort;

    @CommandLine.Option(names = "--disable-legacy-endpoints",
            description = "Do not register (deprecated) Service Ednpoints when intercepting scaled down servicess",
            defaultValue = "${DISABLE_LEGACY_ENDPOINTS}"
    )
    boolean disableLegacyEndpoints;

    @CommandLine.Option(names = "--scale-up-timeout",
            description = "Maximum time to wait for a service to become ready before returning 503 (default: 60s). Env: SCALE_UP_TIMEOUT.",
            defaultValue = "${SCALE_UP_TIMEOUT:-60s}")
    String scaleUpTimeout = "60s";

    @CommandLine.Option(names = "--propagation-delay",
            description = "How long to wait after scale-up before redirecting clients, to allow the ingress controller to propagate endpoint changes (default: 2s). Env: PROPAGATION_DELAY.",
            defaultValue = "${PROPAGATION_DELAY:-2s}")
    String propagationDelay = "2s";
}
