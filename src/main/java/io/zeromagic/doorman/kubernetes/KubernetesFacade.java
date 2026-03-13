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


import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

// this is just collecting our needs of kubernetes client, we should merge all of these interfaces into single one
public interface KubernetesFacade extends EndpointRegistrar, DeploymentStateReader, ScalingPolicyStatusPatcher, ServiceScaler{
    <T extends HasMetadata> AutoCloseable inform(Class<T> resourceType, String namespace, String labelSelectors, ResourceEventHandler<T> handler);
}
