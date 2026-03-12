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

import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.repository.crd.ScalingPolicy;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ScalingPolicyInformer implements ResourceEventHandler<ScalingPolicy> {

    private static final Logger LOG = LoggerFactory.getLogger(ScalingPolicyInformer.class);

    private final ScalingPolicyEvents events;

    ScalingPolicyInformer(ScalingPolicyEvents events) {
        this.events = events;
    }

    @Override
    public void onAdd(ScalingPolicy obj) {
        LOG.info("ScalingPolicy ADDED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
        events.onAdded(obj);
    }

    @Override
    public void onUpdate(ScalingPolicy oldObj, ScalingPolicy newObj) {
        LOG.info("ScalingPolicy UPDATED: {}/{}", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName());
        events.onUpdated(oldObj, newObj);
    }

    @Override
    public void onDelete(ScalingPolicy obj, boolean deletedFinalStateUnknown) {
        LOG.info("ScalingPolicy DELETED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
        events.onDeleted(obj);
    }
}
