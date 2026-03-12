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
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DeploymentInformer implements ResourceEventHandler<Deployment> {

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentInformer.class);

    private final DeploymentEvents events;

    DeploymentInformer(DeploymentEvents events) {
        this.events = events;
    }

    @Override
    public void onAdd(Deployment obj) {
        logDeployment("ADDED", obj);
        events.onDeploymentChanged(obj);
    }

    @Override
    public void onUpdate(Deployment oldObj, Deployment newObj) {
        logDeployment("UPDATED", newObj);
        events.onDeploymentChanged(newObj);
    }

    @Override
    public void onDelete(Deployment obj, boolean deletedFinalStateUnknown) {
        logDeployment("DELETED", obj);
        events.onDeploymentChanged(obj);
    }

    private void logDeployment(String event, Deployment d) {
        var status = d.getStatus();
        LOG.info("Deployment {}: {}/{} replicas={} ready={}",
                event,
                d.getMetadata().getNamespace(),
                d.getMetadata().getName(),
                status != null ? status.getReplicas() : "?",
                status != null ? status.getReadyReplicas() : "?");
    }
}
