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
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.cli.TraefikConfig;
import io.zeromagic.doorman.kubernetes.LabelRestricted;
import io.zeromagic.doorman.kubernetes.NamespaceRestricted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Discovers Traefik pods by namespace + label selector and maintains a live list
 * of metrics endpoint URIs. Registers itself as an informer against the provided
 * {@link KubernetesClient} on construction and closes the informer on {@link #close()}.
 */
public class KubernetesPodMetricsEndpointSource
        implements MetricsEndpointSource, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(KubernetesPodMetricsEndpointSource.class);

    private final TraefikConfig.Discovered config;
    private final ConcurrentHashMap<String, URI> podEndpoints = new ConcurrentHashMap<>();
    private final AutoCloseable informerHandle;

    public KubernetesPodMetricsEndpointSource(TraefikConfig.Discovered config, KubernetesClient client) {
        this.config = config;
        this.informerHandle = client.resources(Pod.class)
                .inNamespace(config.namespace())
                .withLabelSelector(config.labelSelector())
                .inform(new ResourceEventHandler<Pod>() {
                    @Override
                    public void onAdd(Pod obj) {
                        register(obj);
                    }

                    @Override
                    public void onUpdate(Pod oldObj, Pod newObj) {
                        register(newObj);
                    }

                    @Override
                    public void onDelete(Pod obj, boolean deletedFinalStateUnknown) {
                        String name = pod.getMetadata().getName();
                        podEndpoints.remove(name);
                        LOG.debug("Removed metrics endpoint for pod {}", name);
                    }
                });
    }

    private void register(Pod pod) {
        String name = pod.getMetadata().getName();
        String ip = pod.getStatus() != null ? pod.getStatus().getPodIP() : null;
        if (ip == null || ip.isBlank()) {
            return;
        }
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
