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

import io.avaje.inject.Bean;
import io.avaje.inject.Factory;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.cli.TraefikConfig;

@Factory
class TrafficFactory {

    @Bean(autoCloseable = true)
    MetricsEndpointSource metricsEndpointSource(TraefikConfig config, KubernetesFacade facade) {
        return switch (config) {
            case TraefikConfig.Direct d -> new FixedMetricsEndpointSource(d);
            case TraefikConfig.Discovered disc -> new KubernetesPodMetricsEndpointSource(disc, facade);
        };
    }
}
