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

import io.zeromagic.doorman.kubernetes.EndpointRegistrar;

/**
 * Events derived from the EndpointSlice API (Traefik v3+ / newer clusters).
 * Methods are named with "Slice" suffix to distinguish from the identically
 * shaped {@link EndpointsEvents} (classic API). Both may fire for the same
 * service — {@link EndpointRegistrar} implementations must be idempotent.
 *
 * Kubernetes will continuously remove Doorman's IP from slices it does not own
 * because Doorman's pod lacks the service selector labels. The fight-back loop
 * is triggered by {@link #onDoormanSliceRemoved}.
 */
public interface EndpointSliceEvents {
    /** All real (non-Doorman) endpoints in all slices for the service have become not-ready. */
    void onRealSlicesDrained(String namespace, String serviceName);

    /** Doorman's own IP was removed from the EndpointSlice(s) for the service. */
    void onDoormanSliceRemoved(String namespace, String serviceName);

    /** At least one real (non-Doorman) endpoint is ready across the slices for the service. */
    void onRealSlicesReady(String namespace, String serviceName);
}
