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

package io.zeromagic.doorman.traffic;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.kubernetes.KubernetesFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers Traefik pods by namespace + label selector and maintains a live list
 * of metrics endpoint URIs. Registers itself as a pod informer via {@link KubernetesFacade}
 * on construction and closes the informer handle on {@link #close()}.
 */
public class KubernetesPodMetricsEndpointSource
        implements MetricsEndpointSource, ResourceEventHandler<Pod>, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPodMetricsEndpointSource.class);

    private final TraefikConfig.Discovered config;
    private final ConcurrentHashMap<String, URI> podEndpoints = new ConcurrentHashMap<>();
    private final AutoCloseable informerHandle;

    public KubernetesPodMetricsEndpointSource(TraefikConfig.Discovered config, KubernetesFacade facade) {
        this.config = config;
        this.informerHandle = facade.inform(Pod.class, config.namespace(), config.labelSelector(), this);
    }

    @Override
    public void onAdd(Pod pod) {
        register(pod);
    }

    @Override
    public void onUpdate(Pod oldPod, Pod newPod) {
        register(newPod);
    }

    @Override
    public void onDelete(Pod pod, boolean deletedFinalStateUnknown) {
        String name = pod.getMetadata().getName();
        podEndpoints.remove(name);
        LOG.debug("Removed metrics endpoint for pod {}", name);
    }

    private void register(Pod pod) {
        String name = pod.getMetadata().getName();
        String ip = pod.getStatus() != null ? pod.getStatus().getPodIP() : null;
        if (ip == null || ip.isBlank()) return;
        URI uri = URI.create("http://" + ip + ":" + config.metricsPort() + "/metrics");
        podEndpoints.put(name, uri);
        LOG.debug("Registered metrics endpoint for pod {}: {}", name, uri);
    }

    @Override
    public List<URI> endpoints() {
        return List.copyOf(podEndpoints.values());
    }

    @Override
    public void close() throws Exception {
        informerHandle.close();
    }
}
