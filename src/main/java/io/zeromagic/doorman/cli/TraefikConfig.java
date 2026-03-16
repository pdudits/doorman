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

/**
 * Sealed hierarchy representing how to reach Traefik metrics,
 * together with polling and idle-detection timing configuration.
 * Either a single direct URL or pod-discovery via Kubernetes label selector.
 */
public sealed interface TraefikConfig {

    String idleTimeout();
    String metricsPollInterval();

    /** Single static metrics endpoint URL. */
    record Direct(String metricsUrl, String idleTimeout, String metricsPollInterval)
            implements TraefikConfig {}

    /** Discover Traefik pods by namespace + label selector and scrape each one. */
    record Discovered(String namespace, String labelSelector, int metricsPort,
                      String idleTimeout, String metricsPollInterval)
            implements TraefikConfig {}
}
