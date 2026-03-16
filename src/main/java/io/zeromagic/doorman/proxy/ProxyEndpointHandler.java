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
package io.zeromagic.doorman.proxy;

import io.avaje.inject.PostConstruct;
import io.fabric8.kubernetes.api.model.Endpoints;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.DoormanConfig;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import io.zeromagic.doorman.scaling.EndpointRegistrar;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
class ProxyEndpointHandler implements EndpointRegistrar, AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(ProxyEndpointHandler.class);
    private final KubernetesFacade client;
    private final DoormanConfig doormanConfig;
    private AutoCloseable endpointWatch;
    private AutoCloseable sliceWatch;
    private final ConcurrentHashMap<RegisteredService, Object> activelyProxiedEndpoint = new ConcurrentHashMap<>();

    record RegisteredService(String namespace, String serviceName) {
        static RegisteredService ofEndpoint(Endpoints endpoint) {
            return new RegisteredService(endpoint.getMetadata().getNamespace(), endpoint.getMetadata().getName());
        }

        static RegisteredService ofSlice(EndpointSlice slice) {
            return new RegisteredService(slice.getMetadata().getNamespace(), slice.getMetadata().getLabels().get("kubernetes.io/service-name"));
        }
    }


    ProxyEndpointHandler(KubernetesFacade client, DoormanConfig doormanConfig) {
        this.client = client;
        this.doormanConfig = doormanConfig;
    }

    @PostConstruct
    void watch() {
        this.endpointWatch = client.inform(Endpoints.class, new ResourceEventHandler<Endpoints>() {
            @Override
            public void onAdd(Endpoints obj) {

            }

            @Override
            public void onUpdate(Endpoints oldObj, Endpoints newObj) {
                if (relevant(RegisteredService.ofEndpoint(oldObj))) {
                    assureEndpointInstalled(newObj);
                }
            }

            @Override
            public void onDelete(Endpoints obj, boolean deletedFinalStateUnknown) {

            }
        });
        this.sliceWatch = client.inform(EndpointSlice.class, new ResourceEventHandler<EndpointSlice>() {
            @Override
            public void onAdd(EndpointSlice obj) {

            }

            @Override
            public void onUpdate(EndpointSlice oldObj, EndpointSlice newObj) {
                logger.debug("Slice updated: {}", newObj);

            }

            @Override
            public void onDelete(EndpointSlice obj, boolean deletedFinalStateUnknown) {
                RegisteredService service = RegisteredService.ofSlice(obj);
                logger.debug("Slice deleted for {}: {}", service, obj);
                if (relevant(service)) {
                    assureSliceInstalled(service, obj);
                } else {
                    logger.debug("Not relevant");
                }
            }
        });
    }

    private void assureSliceInstalled(RegisteredService service, EndpointSlice obj) {
        if (obj.getEndpoints().stream()
                .anyMatch(endpoint -> endpoint.getAddresses().stream().anyMatch(doormanConfig.podIp()::equals))) {
            logger.debug("Reinstalling slice endpoint {}", obj);
            client.addEndpointSlice(obj);
        }
    }

    private void assureEndpointInstalled(Endpoints newObj) {
        var subsets = newObj.getSubsets();

        logger.debug("Evaluating endpoints: {}", subsets);

        boolean doormanPresent = subsets.stream()
                .flatMap(s -> s.getAddresses() == null ? java.util.stream.Stream.empty() : s.getAddresses().stream())
                .anyMatch(a -> Objects.equals(a.getIp(), doormanConfig.podIp()));

        if (!doormanPresent) {
            logger.debug("Adding Doorman endpoint to {}/{}", newObj.getMetadata().getNamespace(), newObj.getMetadata().getName());
            client.addEndpointSubset(newObj, doormanConfig.podIp(), doormanConfig.proxyPort());
        }
    }

    private boolean relevant(RegisteredService service) {
        return activelyProxiedEndpoint.containsKey(service);
    }

    @Override
    public void register(String namespace, String serviceName) {
        var service = new RegisteredService(namespace, serviceName);
        var alreadyInstalled = activelyProxiedEndpoint.put(service, this);
        if (alreadyInstalled == null) {
            installEndpoints(service);
        }
    }

    private void installEndpoints(RegisteredService service) {
        logger.debug("Installing endpoints for {}", service);
        client.addEndpointSubset(service.namespace(), service.serviceName(), doormanConfig.podIp(), doormanConfig.proxyPort());
        client.addEndpointSlice(service.namespace(), service.serviceName(), doormanConfig.podIp(), doormanConfig.proxyPort());
    }

    @Override
    public void deregister(String namespace, String serviceName) {
        var service = new RegisteredService(namespace, serviceName);
        logger.debug("Removing endpoints for {}", service);
        var wasRegistered = activelyProxiedEndpoint.remove(service);
        if (wasRegistered != null) {
            uninstallEndpoints(service);
        }
    }

    private void uninstallEndpoints(RegisteredService service) {
        client.removeEndpointSubset(service.namespace(), service.serviceName(), doormanConfig.podIp(), doormanConfig.proxyPort());
        client.removeEndpointSlice(service.namespace(), service.serviceName());
    }


    @Override
    public void close() throws Exception {
        if (this.endpointWatch != null) {
            endpointWatch.close();
        }
        if (this.sliceWatch != null) {
            sliceWatch.close();
        }
    }
}
