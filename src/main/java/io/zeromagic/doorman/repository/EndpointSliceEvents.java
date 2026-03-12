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

/**
 * Events derived from the EndpointSlice API (Traefik v3+ / newer clusters).
 * Like {@link EndpointsEvents}, Kubernetes will remove Doorman's entry from
 * slices it does not own. The fight-back loop re-adds / recreates a
 * Doorman-owned slice on {@link #onDoormanEndpointRemoved}.
 */
public interface EndpointSliceEvents {
    /** All real (non-Doorman) endpoints in all slices for the service have become not-ready. */
    void onRealEndpointsDrained(String namespace, String serviceName);

    /** Doorman's own IP was removed from the EndpointSlice(s) for the service. */
    void onDoormanEndpointRemoved(String namespace, String serviceName);

    /** At least one real (non-Doorman) endpoint is ready across the slices for the service. */
    void onRealEndpointsReady(String namespace, String serviceName);
}
