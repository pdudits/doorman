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

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * No-op stubs for all domain event interfaces. Active until Task-004 provides
 * real implementations. Avaje will prefer a more specific bean over this one.
 */
@Singleton
class NoOpDomainEvents implements ScalingPolicyEvents, DeploymentEvents, EndpointsEvents, EndpointSliceEvents {

    private static final Logger LOG = LoggerFactory.getLogger(NoOpDomainEvents.class);

    @Override
    public void onAdded(ScalingPolicy policy) {
        LOG.info("[stub] ScalingPolicy added: {}/{}", policy.getMetadata().getNamespace(), policy.getMetadata().getName());
    }

    @Override
    public void onUpdated(ScalingPolicy oldPolicy, ScalingPolicy newPolicy) {
        LOG.info("[stub] ScalingPolicy updated: {}/{}", newPolicy.getMetadata().getNamespace(), newPolicy.getMetadata().getName());
    }

    @Override
    public void onDeleted(ScalingPolicy policy) {
        LOG.info("[stub] ScalingPolicy deleted: {}/{}", policy.getMetadata().getNamespace(), policy.getMetadata().getName());
    }

    @Override
    public void onDeploymentChanged(Deployment deployment) {
        LOG.info("[stub] Deployment changed: {}/{} ready={}/{}",
                deployment.getMetadata().getNamespace(),
                deployment.getMetadata().getName(),
                deployment.getStatus() != null ? deployment.getStatus().getReadyReplicas() : "?",
                deployment.getStatus() != null ? deployment.getStatus().getReplicas() : "?");
    }

    @Override
    public void onRealEndpointsDrained(String namespace, String serviceName) {
        LOG.info("[stub] Real endpoints drained: {}/{}", namespace, serviceName);
    }

    @Override
    public void onDoormanEndpointRemoved(String namespace, String serviceName) {
        LOG.info("[stub] Doorman endpoint removed: {}/{}", namespace, serviceName);
    }

    @Override
    public void onRealEndpointsReady(String namespace, String serviceName) {
        LOG.info("[stub] Real endpoints ready: {}/{}", namespace, serviceName);
    }
}
