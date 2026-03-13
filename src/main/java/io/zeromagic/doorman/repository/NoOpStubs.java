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

package io.zeromagic.doorman.repository;

import io.zeromagic.doorman.kubernetes.EndpointRegistrar;
import io.zeromagic.doorman.kubernetes.ServiceScaler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** No-op stubs active until Task-006 (EndpointRegistrar) and Task-008 (ServiceScaler) are implemented. */
@Singleton
class NoOpStubs implements ServiceScaler, EndpointRegistrar {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpStubs.class);

    @Override
    public void scaleUp(String namespace, String deploymentName, int targetReplicas) {
        LOG.info("[stub] scaleUp {}/{} to {}", namespace, deploymentName, targetReplicas);
    }

    @Override
    public void scaleDown(String namespace, String deploymentName) {
        LOG.info("[stub] scaleDown {}/{}", namespace, deploymentName);
    }

    @Override
    public void register(String namespace, String serviceName) {
        LOG.info("[stub] register endpoint {}/{}", namespace, serviceName);
    }

    @Override
    public void deregister(String namespace, String serviceName) {
        LOG.info("[stub] deregister endpoint {}/{}", namespace, serviceName);
    }
}
