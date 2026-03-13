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

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ServiceInformer implements ResourceEventHandler<Service> {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceInformer.class);

    private final KubernetesFacade facade;
    private AutoCloseable informerHandle;

    ServiceInformer(KubernetesFacade facade) {
        this.facade = facade;
    }

    @PostConstruct
    void start() {
        informerHandle = facade.inform(Service.class, this);
    }

    @PreDestroy
    void close() throws Exception {
        if (informerHandle != null) informerHandle.close();
    }

    @Override
    public void onAdd(Service obj) {
        LOG.info("Service ADDED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
    }

    @Override
    public void onUpdate(Service oldObj, Service newObj) {
        LOG.info("Service UPDATED: {}/{}", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName());
    }

    @Override
    public void onDelete(Service obj, boolean deletedFinalStateUnknown) {
        LOG.info("Service DELETED: {}/{}", obj.getMetadata().getNamespace(), obj.getMetadata().getName());
    }
}
