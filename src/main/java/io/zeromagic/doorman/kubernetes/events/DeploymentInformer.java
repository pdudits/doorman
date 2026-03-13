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

package io.zeromagic.doorman.kubernetes.events;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class DeploymentInformer implements ResourceEventHandler<Deployment> {

    private static final Logger LOG = LoggerFactory.getLogger(DeploymentInformer.class);

    private final List<DeploymentEvents> listeners;
    private final KubernetesFacade facade;
    private AutoCloseable informerHandle;

    DeploymentInformer(List<DeploymentEvents> listeners, KubernetesFacade facade) {
        this.listeners = listeners;
        this.facade = facade;
    }

    @PostConstruct
    void start() {
        informerHandle = facade.inform(Deployment.class, this);
    }

    @PreDestroy
    void close() throws Exception {
        if (informerHandle != null) informerHandle.close();
    }

    @Override
    public void onAdd(Deployment obj) {
        logDeployment("ADDED", obj);
        listeners.forEach(l -> l.onDeploymentChanged(obj));
    }

    @Override
    public void onUpdate(Deployment oldObj, Deployment newObj) {
        logDeployment("UPDATED", newObj);
        listeners.forEach(l -> l.onDeploymentChanged(newObj));
    }

    @Override
    public void onDelete(Deployment obj, boolean deletedFinalStateUnknown) {
        logDeployment("DELETED", obj);
        listeners.forEach(l -> l.onDeploymentChanged(obj));
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
