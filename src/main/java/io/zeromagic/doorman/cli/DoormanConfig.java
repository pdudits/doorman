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

package io.zeromagic.doorman.cli;

/**
 * Runtime identity of this Doorman instance.
 * podIp is used when registering Doorman as a service endpoint.
 * proxyPort is the port Doorman's HTTP proxy listens on (registered in endpoints).
 * scaleUpTimeout is the maximum time to wait for a service to become ready before returning 503.
 * propagationDelay is the time to wait after scale-up before redirecting, allowing the ingress controller to propagate endpoint changes.
 */
public record DoormanConfig(String podIp, int proxyPort, java.time.Duration scaleUpTimeout, java.time.Duration propagationDelay) {
}
