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

package io.zeromagic.doorman.scaling;

import io.zeromagic.doorman.kubernetes.ServiceScaler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * Pure data/state object for a single managed application. Contains no
 * Kubernetes client calls or side effects beyond logging.
 * All external side effects are driven by {@link ScaledApplicationRegistry}.
 *
 * <p>Valid transitions and their callers:
 * <pre>
 *   Running      → ScalingDown   beginScalingDown()  (idle detector, Task-005)
 *   ScalingDown  → ScaledDown    confirmScaledDown() (deployment/endpoints event)
 *   ScaledDown   → ScalingUp     awaitReady()        (HTTP proxy)
 *   ScalingUp    → Running       confirmRunning()    (deployment event, ≥1 ready pod)
 * </pre>
 */
public class ScaledApplication {

    private static final Logger LOG = LoggerFactory.getLogger(ScaledApplication.class);

    /**
     * Immutable snapshot of the ScalingPolicy spec fields needed at runtime.
     * {@code targetReplicas} is sourced from status (saved at scale-down time).
     */
    public record Snapshot(String namespace, String policyName,
                           String serviceName, String deploymentName,
                           int targetReplicas,
                           Duration idleTimeout) {}

    /**
     * Result of {@link #awaitReady()}.
     * {@code scaleUpNeeded} is true only for the first caller that triggered the
     * {@code ScaledDown → ScalingUp} transition; all subsequent callers get false.
     * The registry uses this to fire {@link ServiceScaler#scaleUp} exactly once.
     * The future completes with {@code null} when the service is ready; the proxy
     * layer is responsible for determining the redirect URL from the request.
     */
    public record AwaitResult(CompletableFuture<Void> future, boolean scaleUpNeeded) {
        static AwaitResult running() {
            return new AwaitResult(CompletableFuture.completedFuture(null), false);
        }
        static AwaitResult initiated(CompletableFuture<Void> f) {
            return new AwaitResult(f, true);
        }
        static AwaitResult pending(CompletableFuture<Void> f) {
            return new AwaitResult(f, false);
        }
        static AwaitResult unavailable(String reason) {
            return new AwaitResult(CompletableFuture.failedFuture(new IllegalStateException(reason)), false);
        }
    }

    private volatile Snapshot snapshot;
    private final AtomicReference<ServiceState> state;

    public ScaledApplication(Snapshot snapshot, ServiceState initialState) {
        this.snapshot = snapshot;
        this.state = new AtomicReference<>(initialState);
    }

    public Snapshot snapshot() { return snapshot; }
    public void updateSnapshot(Snapshot snapshot) { this.snapshot = snapshot; }
    public ServiceState currentState() { return state.get(); }

    // -------------------------------------------------------------------------
    // Named transitions
    // -------------------------------------------------------------------------

    /**
     * {@code Running → ScalingDown}. Returns true if the transition happened.
     * Called by the idle detector (Task-005) when traffic has been absent long enough.
     */
    public boolean beginScalingDown() {
        return cas(
                s -> s instanceof ServiceState.Running,
                s -> new ServiceState.ScalingDown()
        ).preconditionMatched();
    }

    /**
     * {@code ScalingDown → ScaledDown}. Creates the {@link CompletableFuture} that
     * proxy threads will block on. Returns true if the transition happened.
     * Called when the deployment reaches zero ready replicas or endpoints drain.
     */
    public boolean confirmScaledDown() {
        return cas(
                s -> s instanceof ServiceState.ScalingDown,
                s -> new ServiceState.ScaledDown(new CompletableFuture<>())
        ).preconditionMatched();
    }

    /**
     * Proxy entry point. Atomically transitions {@code ScaledDown → ScalingUp}
     * for the first caller, which receives {@link AwaitResult#scaleUpNeeded()} == true.
     * All callers (including concurrent ones) receive the same future.
     * Returns a completed future for Running, failed future for Stopped/ScalingDown.
     */
    public AwaitResult awaitReady() {
        while (true) {
            ServiceState current = state.get();
            switch (current) {
                case ServiceState.Running() ->
                        { return AwaitResult.running(); }
                case ServiceState.ScaledDown(var future) -> {
                    var next = new ServiceState.ScalingUp(future);
                    if (state.compareAndSet(current, next)) {
                        LOG.info("{}/{}: ScaledDown → ScalingUp (scale-up initiated)",
                                snapshot.namespace(), snapshot.policyName());
                        return AwaitResult.initiated(future);
                    }
                    // CAS lost to another thread — retry
                }
                case ServiceState.ScalingUp(var future) ->
                        { return AwaitResult.pending(future); }
                case ServiceState.ScalingDown() ->
                        { return AwaitResult.unavailable(snapshot.serviceName() + " is currently scaling down"); }
                case ServiceState.Stopped() ->
                        { return AwaitResult.unavailable(snapshot.serviceName() + " is stopped"); }
            }
        }
    }

    /**
     * {@code ScalingUp → Running}. Atomically captures the in-flight future from
     * the {@code ScalingUp} state, transitions to {@code Running}, then completes
     * the future — unblocking all waiting proxy threads.
     * Returns true if the transition happened.
     */
    public boolean confirmRunning() {
        var result = cas(ServiceState.ScalingUp.class::isInstance, (x) -> new ServiceState.Running());

        if (result.previous() instanceof ServiceState.ScalingUp(var future)) {
            LOG.info("{}/{}: ScalingUp → Running", snapshot.namespace(), snapshot.policyName());
            future.complete(null);
            return true;
        }
        return false;
    }

    /**
     * Cancels any in-flight future. Called by the registry when the ScalingPolicy
     * is deleted while a scale-up is in progress.
     */
    public void cancelPendingFuture() {
        switch (state.get()) {
            case ServiceState.ScaledDown(var future) -> future.cancel(true);
            case ServiceState.ScalingUp(var future) -> future.cancel(true);
            default -> {} // nothing to cancel
        }
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    /** CAS helper: apply {@code fn} only when {@code guard} holds for current state. */
    private  CasResult cas(java.util.function.Predicate<ServiceState> guard,
                        UnaryOperator<ServiceState> fn) {
        ServiceState old;
        ServiceState next;
        do {
            old = state.get();
            if (!guard.test(old)) return new CasResult(false, old, old);
            next = fn.apply(old);
        } while (!state.compareAndSet(old, next));
        LOG.info("{}/{}: {} → {}", snapshot.namespace(), snapshot.policyName(),
                old.getClass().getSimpleName(), next.getClass().getSimpleName());
        return new  CasResult(true, old, next);
    }

    record CasResult(boolean preconditionMatched, ServiceState previous, ServiceState curret) {}
}

