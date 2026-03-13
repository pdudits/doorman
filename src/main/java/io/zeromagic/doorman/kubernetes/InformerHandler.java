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


import io.avaje.inject.BeanScope;
import io.avaje.inject.PostConstruct;
import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Singleton
class InformerHandler implements  AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(InformerHandler.class);
    private final KubernetesClient client;
    private final List<AutoCloseable> informers = new ArrayList<>();

    InformerHandler(KubernetesClient kubernetesClient) {
        this.client = kubernetesClient;

    }

    @PostConstruct
    @SuppressWarnings("raw")
    void registerInformers(BeanScope beanScope) {
        var informers = beanScope.list(ResourceEventHandler.class);
        for (ResourceEventHandler informer : informers) {
            var resourceType = Stream.of(informer.getClass().getGenericInterfaces())
                .filter(i -> i instanceof java.lang.reflect.ParameterizedType)
                .map(i -> (java.lang.reflect.ParameterizedType) i)
                .filter(i -> i.getRawType() == ResourceEventHandler.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to determine resource type for informer: " + informer.getClass()))
                .getActualTypeArguments()[0];

            //noinspection unchecked
            var operation = client.resources((Class<? extends HasMetadata>) resourceType);
            var scoped = informer instanceof NamespaceRestricted ns
                    ? operation.inNamespace(ns.namespace())
                    : operation;
            var filtered = (informer instanceof LabelRestricted ls)
                    ? scoped.withLabelSelector(ls.labelSelector())
                    : scoped;
            this.informers.add(filtered.inform(informer));
        }
    }

    @Override
    public void close() {
        for (AutoCloseable informer : informers) {
            try {
                informer.close();
            } catch (Exception e) {
                LOGGER.warn("Unable to close informer: " + informer.getClass(), e);
            }
        }
    }
}
