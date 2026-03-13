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

import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.avaje.inject.PostConstruct;
import io.avaje.inject.PreDestroy;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Watches the classic Endpoints API for Traefik v2 and older clusters.
 *
 * Kubernetes will continuously remove Doorman's IP from Endpoints because
 * Doorman's pod does not carry the service selector labels. The fight-back
 * loop is triggered by {@link EndpointsEvents#onDoormanEndpointRemoved}.
 */
@Singleton
public class EndpointsInformer implements ResourceEventHandler<Endpoints> {

    private static final Logger LOG = LoggerFactory.getLogger(EndpointsInformer.class);

    private final List<EndpointsEvents> listeners;
    private final String doormanIp;
    private final KubernetesFacade facade;
    private AutoCloseable informerHandle;

    EndpointsInformer(List<EndpointsEvents> listeners, DoormanConfig config, KubernetesFacade facade) {
        this.listeners = listeners;
        this.doormanIp = config.podIp();
        this.facade = facade;
    }

    @PostConstruct
    void start() {
        informerHandle = facade.inform(Endpoints.class, this);
    }

    @PreDestroy
    void close() throws Exception {
        if (informerHandle != null) informerHandle.close();
    }

    @Override
    public void onAdd(Endpoints obj) {
        evaluate(obj);
    }

    @Override
    public void onUpdate(Endpoints oldObj, Endpoints newObj) {
        evaluate(newObj);
    }

    @Override
    public void onDelete(Endpoints obj, boolean deletedFinalStateUnknown) {
        // deletion means no endpoints at all — treat as drained
        var ns = obj.getMetadata().getNamespace();
        var svc = obj.getMetadata().getName();
        LOG.info("Endpoints DELETED: {}/{}", ns, svc);
        listeners.forEach(l -> l.onRealEndpointsDrained(ns, svc));
    }

    private void evaluate(Endpoints ep) {
        var ns = ep.getMetadata().getNamespace();
        var svc = ep.getMetadata().getName();

        var subsets = ep.getSubsets();
        if (subsets == null) subsets = List.of();

        boolean doormanPresent = subsets.stream()
                .flatMap(s -> s.getAddresses() == null ? java.util.stream.Stream.empty() : s.getAddresses().stream())
                .anyMatch(a -> Objects.equals(a.getIp(), doormanIp));

        boolean realReady = subsets.stream()
                .flatMap(s -> s.getAddresses() == null ? java.util.stream.Stream.empty() : s.getAddresses().stream())
                .anyMatch(a -> !Objects.equals(a.getIp(), doormanIp));

        LOG.info("Endpoints {}/{}: doormanPresent={} realReady={}", ns, svc, doormanPresent, realReady);

        if (!doormanPresent) {
            listeners.forEach(l -> l.onDoormanEndpointRemoved(ns, svc));
        }
        if (realReady) {
            listeners.forEach(l -> l.onRealEndpointsReady(ns, svc));
        } else {
            listeners.forEach(l -> l.onRealEndpointsDrained(ns, svc));
        }
    }
}
