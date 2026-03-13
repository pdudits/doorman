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

/**
 * Registers/deregisters Doorman's own IP as a service endpoint.
 * Real impl in Task-006. Must be idempotent — both EndpointsInformer
 * and EndpointSliceInformer may call register() for the same service.
 */
public interface EndpointRegistrar {
    void register(String namespace, String serviceName);
    void deregister(String namespace, String serviceName);
}
