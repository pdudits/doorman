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

import java.util.Optional;

/**
 * Reads current deployment state for initial phase detection on ScalingPolicy add.
 * Decoupled from KubernetesClient so the registry is unit-testable.
 */
public interface DeploymentStateReader {

    record DeploymentState(int specReplicas, int readyReplicas) {}

    /** Returns empty if the deployment does not exist. */
    Optional<DeploymentState> read(String namespace, String deploymentName);
}
