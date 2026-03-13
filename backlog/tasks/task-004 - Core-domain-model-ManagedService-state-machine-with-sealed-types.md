---
id: TASK-004
title: 'Core domain model: ManagedService state machine with sealed types'
status: Done
assignee: []
created_date: '2026-03-12 11:26'
updated_date: '2026-03-13 11:14'
labels:
  - core
  - state-machine
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Define the core domain model for the lifecycle state machine of a managed application. This is the central data structure all other components read from and write to.\n\n**`ScaledApplication`** is a pure data/state object — no K8s calls, no side effects:\n- Holds `AtomicReference<ServiceState>` with sealed-type states: `Running`, `ScalingDown`, `ScaledDown(CompletableFuture)`, `ScalingUp(CompletableFuture)`, `Stopped`\n- `transition(fn)` — pure CAS loop, logs old→new, returns new state\n- `awaitReady()` — atomically transitions `ScaledDown→ScalingUp` (returning `scaleUpNeeded=true` for the first caller); subsequent callers get same future; returns failed future for Stopped/ScalingDown\n- `notifyReady(url)` / `cancelPendingFuture(reason)` for completing/cancelling the future\n\n**`ScaledApplicationRegistry`** is the coordinator `@Singleton` implementing all 4 event interfaces. It delegates to `ScaledApplication` for state transitions, then calls the appropriate side-effect interface. Three injectable stub interfaces keep K8s calls out of the state machine: `ScalingPolicyStatusPatcher`, `ServiceScaler` (Task-008), `EndpointRegistrar` (Task-006), `DeploymentStateReader`.\n\n25+ unit tests, no mocking framework needed — constructor-injected lambdas.")
<parameter name="acceptanceCriteriaSet">["ServiceState is a sealed interface with records Running, ScalingDown, ScaledDown(future), ScalingUp(future), Stopped", "ScaledApplication is a pure data object: AtomicReference<ServiceState> + CAS transition + awaitReady() + notifyReady() + cancelPendingFuture()", "ScaledApplicationRegistry implements ScalingPolicyEvents, DeploymentEvents, EndpointsEvents, EndpointSliceEvents", "Pattern-matching switch used for all transition logic", "Concurrent awaitReady() calls on a ScaledDown service produce exactly one ScalingUp transition and one ServiceScaler.scaleUp() call", "Fight-back (EndpointRegistrar.register) only fires when state is ScaledDown or ScalingUp", "ScalingPolicyStatusPatcher is called after every state transition with the correct phase", "Initial phase on onAdded is determined by querying the Deployment: running→Running, specReplicas=0 and status=ScaledDown→ScaledDown, else Stopped", "ScaledApplicationTest covers all state transitions including concurrent awaitReady", "ScaledApplicationRegistryTest covers coordination logic including all event handlers"]
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 A sealed interface / enum models the phases: `Running`, `ScalingDown`, `ScaledDown`, `ScalingUp`
- [x] #2 Each managed service has an associated `ManagedService` record/class holding current phase, config snapshot, and a `CountDownLatch` or `CompletableFuture` used to signal waiting proxy threads
- [x] #3 State transitions are logged and reflected back to the ScalingPolicy `.status.phase` field via a Kubernetes status patch
- [x] #4 Pattern-matching switch is used for all transition logic (no instanceof chains)
- [x] #5 Concurrent state transitions are safe: two watcher events arriving simultaneously cannot corrupt state (use synchronized block or atomic reference)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented the core domain model:

**`ServiceState`** — sealed interface with records `Running`, `ScalingDown`, `ScaledDown(CompletableFuture<Void>)`, `ScalingUp(CompletableFuture<Void>)`, `Stopped`. The future is a pure readiness signal (`CompletableFuture<Void>`) — no URL payload; the proxy layer (Task-007) owns redirect logic.

**`ScaledApplication`** — pure data object with `AtomicReference<ServiceState>` and a private CAS helper `cas()`. Public named transition methods replace the old generic `transition(fn)`:
- `beginScalingDown()` — `Running → ScalingDown`
- `confirmScaledDown()` — `ScalingDown → ScaledDown` (creates new future)
- `awaitReady()` — `ScaledDown → ScalingUp` (returns `AwaitResult` with `scaleUpNeeded` flag)
- `confirmRunning()` — atomically captures future from `ScalingUp`, transitions to `Running`, completes future
- `cancelPendingFuture()` — cancels in-flight future in `ScaledDown` or `ScalingUp`

**`ScaledApplicationRegistry`** — `@Singleton` implementing all 4 event interfaces. Three injectable side-effect interfaces keep K8s out of the state machine: `ScalingPolicyStatusPatcher` (real K8s impl included), `ServiceScaler` (stub, Task-008), `EndpointRegistrar` (stub, Task-006). `DeploymentStateReader` (real K8s impl included) used for initial state detection on `onAdded`.

`EndpointSliceEvents` has distinct method names (`onRealSlicesDrained`, `onDoormanSliceRemoved`, `onRealSlicesReady`) to avoid Java signature collision with `EndpointsEvents` in the registry.

Deleted `NoOpDomainEvents` — superseded by `ScaledApplicationRegistry`.

**40 unit tests** (up from 38), all passing. AssertJ used for fluent assertions throughout. No mocking framework — constructor-injected lambdas/anonymous classes.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
