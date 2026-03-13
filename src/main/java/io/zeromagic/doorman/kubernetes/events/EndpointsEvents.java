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

/**
 * Events derived from the classic Endpoints API (Traefik v2 / older clusters).
 * Kubernetes will actively remove Doorman's endpoint because its pod does not
 * carry the service's selector labels. The fight-back loop re-adds it on
 * {@link #onDoormanEndpointRemoved}.
 */
public interface EndpointsEvents {
    /** All real (non-Doorman) addresses have disappeared from the Endpoints object. */
    void onRealEndpointsDrained(String namespace, String serviceName);

    /** Doorman's own IP was removed from the Endpoints object by the endpoint controller. */
    void onDoormanEndpointRemoved(String namespace, String serviceName);

    /** At least one real (non-Doorman) address is ready in the Endpoints object. */
    void onRealEndpointsReady(String namespace, String serviceName);
}
