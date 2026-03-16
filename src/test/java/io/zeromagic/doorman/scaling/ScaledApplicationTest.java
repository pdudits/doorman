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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ScaledApplicationTest {

    private static ScaledApplication app(ServiceState initial) {
        return new ScaledApplication(
                new ScaledApplication.Snapshot("ns", "policy", "my-svc", "my-deploy", 3, java.time.Duration.ofMinutes(5)),
                initial);
    }

    // -------------------------------------------------------------------------
    // beginScalingDown()
    // -------------------------------------------------------------------------

    @Test
    void beginScalingDown_whenRunning_transitionsToScalingDown() {
        var a = app(new ServiceState.Running());
        assertThat(a.beginScalingDown()).isTrue();
        assertThat(a.currentState()).isInstanceOf(ServiceState.ScalingDown.class);
    }

    @Test
    void beginScalingDown_whenNotRunning_returnsFalse() {
        var a = app(new ServiceState.ScalingDown());
        assertThat(a.beginScalingDown()).isFalse();
        assertThat(a.currentState()).isInstanceOf(ServiceState.ScalingDown.class);
    }

    // -------------------------------------------------------------------------
    // confirmScaledDown()
    // -------------------------------------------------------------------------

    @Test
    void confirmScaledDown_whenScalingDown_transitionsToScaledDown() {
        var a = app(new ServiceState.ScalingDown());
        assertThat(a.confirmScaledDown()).isTrue();
        assertThat(a.currentState()).isInstanceOf(ServiceState.ScaledDown.class);
    }

    @Test
    void confirmScaledDown_whenNotScalingDown_returnsFalse() {
        var a = app(new ServiceState.Running());
        assertThat(a.confirmScaledDown()).isFalse();
        assertThat(a.currentState()).isInstanceOf(ServiceState.Running.class);
    }

    @Test
    void confirmScaledDown_createsNewFuture() {
        var a = app(new ServiceState.ScalingDown());
        a.confirmScaledDown();
        var state = (ServiceState.ScaledDown) a.currentState();
        assertThat(state.ready()).isNotDone();
    }

    // -------------------------------------------------------------------------
    // awaitReady() — Running
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_whenRunning_returnsCompletedFuture() throws Exception {
        var a = app(new ServiceState.Running());
        var result = a.awaitReady();
        assertThat(result.scaleUpNeeded()).isFalse();
        assertThat(result.future()).isCompleted();
        assertThat(result.future().get()).isNull(); // signals readiness, no payload
    }

    // -------------------------------------------------------------------------
    // awaitReady() — ScaledDown → ScalingUp
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_whenScaledDown_transitionsToScalingUp() {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScaledDown(future));

        var result = a.awaitReady();

        assertThat(result.scaleUpNeeded()).as("first caller should signal scale-up").isTrue();
        assertThat(result.future()).isSameAs(future);
        assertThat(a.currentState()).isInstanceOf(ServiceState.ScalingUp.class);
        assertThat(result.future()).isNotDone();
    }

    @Test
    void awaitReady_whenScaledDown_secondCallReturnsSameFuture() {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScaledDown(future));

        var r1 = a.awaitReady();
        var r2 = a.awaitReady(); // state is now ScalingUp

        assertThat(r1.future()).isSameAs(future);
        assertThat(r2.future()).isSameAs(future);
        assertThat(r1.scaleUpNeeded()).isTrue();
        assertThat(r2.scaleUpNeeded()).as("second caller must not trigger scale-up again").isFalse();
    }

    // -------------------------------------------------------------------------
    // awaitReady() — ScalingUp
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_whenScalingUp_returnsSameFuture() {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScalingUp(future));

        var result = a.awaitReady();

        assertThat(result.scaleUpNeeded()).isFalse();
        assertThat(result.future()).isSameAs(future);
    }

    // -------------------------------------------------------------------------
    // awaitReady() — terminal states
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_whenStopped_returnsFailedFuture() {
        var result = app(new ServiceState.Stopped()).awaitReady();
        assertThat(result.scaleUpNeeded()).isFalse();
        assertThat(result.future()).isCompletedExceptionally();
    }

    @Test
    void awaitReady_whenScalingDown_returnsFailedFuture() {
        var result = app(new ServiceState.ScalingDown()).awaitReady();
        assertThat(result.scaleUpNeeded()).isFalse();
        assertThat(result.future()).isCompletedExceptionally();
    }

    // -------------------------------------------------------------------------
    // Concurrency
    // -------------------------------------------------------------------------

    @Test
    void awaitReady_concurrentCalls_exactlyOneScaleUpSignal() throws Exception {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScaledDown(future));

        int threads = 20;
        var executor = Executors.newFixedThreadPool(threads);
        var latch = new CountDownLatch(1);
        var scaleUpCount = new AtomicInteger(0);
        List<Future<ScaledApplication.AwaitResult>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(executor.submit(() -> {
                latch.await();
                return a.awaitReady();
            }));
        }

        latch.countDown(); // release all threads simultaneously
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        for (var f : futures) {
            var r = f.get();
            if (r.scaleUpNeeded()) scaleUpCount.incrementAndGet();
            assertThat(r.future()).as("all threads must share the same future").isSameAs(future);
        }

        assertThat(scaleUpCount.get()).as("scale-up must be initiated exactly once").isEqualTo(1);
        assertThat(a.currentState()).isInstanceOf(ServiceState.ScalingUp.class);
    }

    // -------------------------------------------------------------------------
    // confirmRunning()
    // -------------------------------------------------------------------------

    @Test
    void confirmRunning_whenScalingUp_transitionsToRunningAndCompletesFuture() throws Exception {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScalingUp(future));

        assertThat(a.confirmRunning()).isTrue();

        assertThat(a.currentState()).isInstanceOf(ServiceState.Running.class);
        assertThat(future).isCompleted();
        assertThat(future.get()).isNull();
    }

    @Test
    void confirmRunning_whenNotScalingUp_returnsFalse() {
        var a = app(new ServiceState.Running());
        assertThat(a.confirmRunning()).isFalse();
        assertThat(a.currentState()).isInstanceOf(ServiceState.Running.class);
    }

    @Test
    void confirmRunning_unlocksAllWaitingFutures() throws Exception {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScalingUp(future));

        int threads = 5;
        var executor = Executors.newFixedThreadPool(threads);
        List<Future<Void>> waiting = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            waiting.add(executor.submit(() -> future.get(2, TimeUnit.SECONDS)));
        }

        a.confirmRunning();

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        for (var f : waiting) {
            assertThat(f.get()).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // cancelPendingFuture()
    // -------------------------------------------------------------------------

    @Test
    void cancelPendingFuture_whenScalingUp_cancelsFuture() {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScalingUp(future));
        a.cancelPendingFuture();
        assertThat(future).isCancelled();
    }

    @Test
    void cancelPendingFuture_whenScaledDown_cancelsFuture() {
        var future = new CompletableFuture<Void>();
        var a = app(new ServiceState.ScaledDown(future));
        a.cancelPendingFuture();
        assertThat(future).isCancelled();
    }

    @Test
    void cancelPendingFuture_whenRunning_noOp() {
        var a = app(new ServiceState.Running());
        // just must not throw
        a.cancelPendingFuture();
        assertThat(a.currentState()).isInstanceOf(ServiceState.Running.class);
    }
}


