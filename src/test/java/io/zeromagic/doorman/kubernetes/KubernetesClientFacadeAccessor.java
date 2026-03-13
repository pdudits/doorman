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

import java.util.Optional;

/**
 * Test accessor for the package-private {@link KubernetesClientFacade}.
 * Allows tests outside the {@code kubernetes} package to construct a real facade.
 */
public class KubernetesClientFacadeAccessor {

    private KubernetesClientFacadeAccessor() {}

    /** Creates a facade using the default kubeconfig / in-cluster config. */
    public static KubernetesFacade create() {
        return new KubernetesClientFacade(Optional.empty());
    }

    /** Creates a facade bound to a specific kube context. */
    public static KubernetesFacade create(String kubeContext) {
        return new KubernetesClientFacade(Optional.of(new io.zeromagic.doorman.cli.KubernetesConfig(kubeContext)));
    }
}
