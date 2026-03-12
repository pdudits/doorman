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

package io.zeromagic.doorman.kubernetes;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.zeromagic.doorman.cli.KubernetesConfig;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Optional;

@Singleton
class ClientProvider implements Provider<KubernetesClient> {
    private KubernetesClient client;

    ClientProvider(Optional<KubernetesConfig> config) {
        if (config.isPresent()) {
            var context = config.get().kubeContext();
            this.client = new KubernetesClientBuilder().withConfig(Config.autoConfigure(context)).build();
        } else {
            this.client = new KubernetesClientBuilder().build();
        }
    }

    @Override
    public KubernetesClient get() {
        return client;
    }
}
