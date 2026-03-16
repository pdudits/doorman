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

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class ScalingPolicyInformer implements ResourceEventHandler<ScalingPolicy> {

    private static final Logger LOG = LoggerFactory.getLogger(ScalingPolicyInformer.class);

    private final List<ScalingPolicyEvents> listeners;
    private final KubernetesFacade facade;
    private AutoCloseable informerHandle;

    ScalingPolicyInformer(List<ScalingPolicyEvents> listeners, KubernetesFacade facade) {
        this.listeners = listeners;
        this.facade = facade;
    }

    @PostConstruct
    void start() {
        informerHandle = facade.inform(ScalingPolicy.class, this);
    }

    @PreDestroy
    void close() throws Exception {
        if (informerHandle != null) informerHandle.close();
    }

    @Override
    public void onAdd(ScalingPolicy obj) {
        LOG.info("ScalingPolicy ADDED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
        listeners.forEach(l -> l.onPolicyAdded(obj));
    }

    @Override
    public void onUpdate(ScalingPolicy oldObj, ScalingPolicy newObj) {
        LOG.info("ScalingPolicy UPDATED: {}/{}", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName());
        listeners.forEach(l -> l.onPolicyUpdated(oldObj, newObj));
    }

    @Override
    public void onDelete(ScalingPolicy obj, boolean deletedFinalStateUnknown) {
        LOG.info("ScalingPolicy DELETED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
        listeners.forEach(l -> l.onPolicyDeleted(obj));
    }
}
