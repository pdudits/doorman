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

import java.util.concurrent.CompletableFuture;

public sealed interface ServiceState
        permits ServiceState.Running, ServiceState.ScalingDown,
                ServiceState.ScaledDown, ServiceState.ScalingUp, ServiceState.Stopped {

    record Running() implements ServiceState {}
    record ScalingDown() implements ServiceState {}
    /** Future is created when entering this state; carried into ScalingUp. */
    record ScaledDown(CompletableFuture<Void> ready) implements ServiceState {}
    /** Same future as ScaledDown; completed when service returns to Running. */
    record ScalingUp(CompletableFuture<Void> ready) implements ServiceState {}
    record Stopped() implements ServiceState {}
}
