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

import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
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
 * Watches EndpointSlice resources for Traefik v3+ and newer clusters.
 *
 * Kubernetes will continuously remove Doorman's IP from slices it does not own
 * because Doorman's pod lacks the service selector labels. The fight-back loop
 * is triggered by {@link EndpointSliceEvents#onDoormanSliceRemoved(String, String)}.
 *
 * Slices are filtered by the {@code kubernetes.io/service-name} label.
 */
@Singleton
public class EndpointSliceInformer implements ResourceEventHandler<EndpointSlice> {

    private static final String SERVICE_NAME_LABEL = "kubernetes.io/service-name";
    private static final Logger LOG = LoggerFactory.getLogger(EndpointSliceInformer.class);

    private final List<EndpointSliceEvents> listeners;
    private final String doormanIp;
    private final KubernetesFacade facade;
    private AutoCloseable informerHandle;

    EndpointSliceInformer(List<EndpointSliceEvents> listeners, DoormanConfig config, KubernetesFacade facade) {
        this.listeners = listeners;
        this.doormanIp = config.podIp();
        this.facade = facade;
    }

    @PostConstruct
    void start() {
        informerHandle = facade.inform(EndpointSlice.class, this);
    }

    @PreDestroy
    void close() throws Exception {
        if (informerHandle != null) informerHandle.close();
    }

    @Override
    public void onAdd(EndpointSlice obj) {
        evaluate(obj);
    }

    @Override
    public void onUpdate(EndpointSlice oldObj, EndpointSlice newObj) {
        evaluate(newObj);
    }

    @Override
    public void onDelete(EndpointSlice obj, boolean deletedFinalStateUnknown) {
        var ns = obj.getMetadata().getNamespace();
        var svc = serviceNameOf(obj);
        if (svc == null) return;
        LOG.info("EndpointSlice DELETED: {}/{}", ns, svc);
        listeners.forEach(l -> l.onRealSlicesDrained(ns, svc));
    }

    private void evaluate(EndpointSlice slice) {
        var ns = slice.getMetadata().getNamespace();
        var svc = serviceNameOf(slice);
        if (svc == null) return;

        var endpoints = slice.getEndpoints();
        if (endpoints == null) endpoints = List.of();

        boolean doormanPresent = endpoints.stream()
                .flatMap(e -> e.getAddresses() == null ? java.util.stream.Stream.empty() : e.getAddresses().stream())
                .anyMatch(addr -> Objects.equals(addr, doormanIp));

        boolean realReady = endpoints.stream()
                .filter(e -> !e.getAddresses().contains(doormanIp))
                .anyMatch(e -> e.getConditions() != null && Boolean.TRUE.equals(e.getConditions().getReady()));

        LOG.info("EndpointSlice {}/{}: doormanPresent={} realReady={}", ns, svc, doormanPresent, realReady);

        if (!doormanPresent) {
            listeners.forEach(l -> l.onDoormanSliceRemoved(ns, svc));
        }
        if (realReady) {
            listeners.forEach(l -> l.onRealSlicesReady(ns, svc));
        } else {
            listeners.forEach(l -> l.onRealSlicesDrained(ns, svc));
        }
    }

    private String serviceNameOf(EndpointSlice slice) {
        var labels = slice.getMetadata().getLabels();
        return labels != null ? labels.get(SERVICE_NAME_LABEL) : null;
    }
}
