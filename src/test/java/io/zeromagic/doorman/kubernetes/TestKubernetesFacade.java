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

package io.zeromagic.doorman.kubernetes;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import io.zeromagic.doorman.kubernetes.crd.ScalingPolicyPhase;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Test-only implementation of {@link KubernetesFacade}.
 * Captures registered informer handlers by resource type so tests can simulate
 * Kubernetes events by calling {@link #fireAdd}, {@link #fireUpdate}, {@link #fireDelete}.
 * All other methods are no-ops / return safe empty values.
 */
public class TestKubernetesFacade implements KubernetesFacade {

    private final Map<Class<?>, ResourceEventHandler<?>> handlers = new HashMap<>();
    private final Map<String, Ingress> ingresses = new HashMap<>();

    @Override
    public <T extends HasMetadata> AutoCloseable inform(
            Class<T> type, String namespace, String labelSelector, ResourceEventHandler<T> handler) {
        handlers.put(type, handler);
        return () -> handlers.remove(type);
    }

    @SuppressWarnings("unchecked")
    public <T extends HasMetadata> void fireAdd(T resource) {
        var handler = (ResourceEventHandler<T>) handlers.get(resource.getClass());
        if (handler != null) handler.onAdd(resource);
    }

    @SuppressWarnings("unchecked")
    public <T extends HasMetadata> void fireUpdate(T oldResource, T newResource) {
        var handler = (ResourceEventHandler<T>) handlers.get(newResource.getClass());
        if (handler != null) handler.onUpdate(oldResource, newResource);
    }

    @SuppressWarnings("unchecked")
    public <T extends HasMetadata> void fireDelete(T resource) {
        var handler = (ResourceEventHandler<T>) handlers.get(resource.getClass());
        if (handler != null) handler.onDelete(resource, false);
    }

    // ── No-op implementations ─────────────────────────────────────────────────

    @Override public Optional<DeploymentState> read(String namespace, String deploymentName) { return Optional.empty(); }
    @Override public void patch(String namespace, String name, ScalingPolicyPhase phase, Integer targetReplicas, String message) {}
    @Override public void register(String namespace, String serviceName) {}
    @Override public void deregister(String namespace, String serviceName) {}
    @Override public void scaleUp(String namespace, String deploymentName, int targetReplicas) {}
    @Override public void scaleDown(String namespace, String deploymentName) {}

    // ── Ingress stubs ─────────────────────────────────────────────────────────

    public void stubIngress(Ingress ingress) {
        String key = ingress.getMetadata().getNamespace() + "/" + ingress.getMetadata().getName();
        ingresses.put(key, ingress);
    }

    public void removeIngress(String namespace, String name) {
        ingresses.remove(namespace + "/" + name);
    }

    @Override
    public Optional<Ingress> getIngress(String namespace, String name) {
        return Optional.ofNullable(ingresses.get(namespace + "/" + name));
    }
}
