---
id: TASK-004
title: 'Core domain model: ManagedService state machine with sealed types'
status: To Do
assignee: []
created_date: '2026-03-12 11:26'
labels:
  - core
  - state-machine
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Define the core domain model for the lifecycle state machine of a managed service. This is the central data structure that all other components (watchers, traffic monitor, proxy, scaler) read from and write to.

Key design choices:
- Use sealed interfaces + records to model states (Java 21 pattern matching)
- The state object must carry a signal (`CountDownLatch`/`CompletableFuture`) that proxy threads block on while waiting for scale-up to complete
- State transitions must be atomic/thread-safe since watchers and HTTP threads share this state
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A sealed interface / enum models the phases: `Running`, `ScalingDown`, `ScaledDown`, `ScalingUp`
- [ ] #2 Each managed service has an associated `ManagedService` record/class holding current phase, config snapshot, and a `CountDownLatch` or `CompletableFuture` used to signal waiting proxy threads
- [ ] #3 State transitions are logged and reflected back to the ScalingPolicy `.status.phase` field via a Kubernetes status patch
- [ ] #4 Pattern-matching switch is used for all transition logic (no instanceof chains)
- [ ] #5 Concurrent state transitions are safe: two watcher events arriving simultaneously cannot corrupt state (use synchronized block or atomic reference)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
