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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;

import java.util.Optional;

/**
 * Single interface to all Kubernetes operations needed by Doorman.
 * The production implementation is {@link KubernetesClientFacade}.
 */
public interface KubernetesFacade extends DeploymentStateReader, ScalingPolicyStatusPatcher, ServiceScaler {

    /** Watch resources of the given type, optionally scoped to namespace and/or label selector. */
    <T extends HasMetadata> AutoCloseable inform(
            Class<T> resourceType, String namespace, String labelSelector, ResourceEventHandler<T> handler);

    /** Watch resources cluster-wide with no label filter. */
    default <T extends HasMetadata> AutoCloseable inform(
            Class<T> resourceType, ResourceEventHandler<T> handler) {
        return inform(resourceType, null, null, handler);
    }

    /** Fetch an Ingress resource by namespace and name. Returns empty if not found. */
    Optional<Ingress> getIngress(String namespace, String name);

    void addEndpointSubset(Endpoints newObj, String ip, int port);

    void addEndpointSubset(String namespace, String serviceName, String ip, int port);

    void removeEndpointSubset(String namespace, String serviceName, String ip, int port);

    void addEndpointSlice(String namespace, String serviceName, String ip, int port);

    void removeEndpointSlice(String namespace, String serviceName);

    void addEndpointSlice(EndpointSlice obj);
}
