---
id: TASK-013.04
title: 'Fix: ScaledDown to Running direct transition on manual scale-up'
status: To Do
assignee: []
created_date: '2026-03-13 23:40'
labels:
  - bug
  - scaling
  - state-machine
milestone: m-0
dependencies: []
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Fix a state machine bug: when `onDeploymentChanged` fires with `readyReplicas >= 1` and Doorman is in `ScaledDown` state (no pending `awaitReady` — e.g. manual scale-up or Doorman restart while service was running), `confirmRunning()` currently returns false and state stays `ScaledDown`.

**The bug consequence**: The next incoming proxy request sees `ScaledDown`, calls `awaitReady()`, which transitions to `ScalingUp` and issues `scaleUp()` on an already-running deployment.

**Root cause**: `ScaledApplication.confirmRunning()` only handles `ScalingUp → Running`. `ScaledDown` is not in the switch.

**Fix** in `ScaledApplication.confirmRunning()`:
```java
next = switch (old) {
    case ServiceState.ScalingUp ignored -> new ServiceState.Running();
    case ServiceState.ScaledDown ignored -> new ServiceState.Running(); // add
    default -> old;
};
// after CAS loop:
if (old instanceof ServiceState.ScalingUp(var future)) {
    future.complete(null);
    return true;
}
if (old instanceof ServiceState.ScaledDown) {
    return true; // state transitioned, no future to complete
}
return false;
```

`onDeploymentChanged` already calls `registrar.deregister()` before `confirmRunning()` — ordering preserved.

**Key files:**
- `src/main/java/io/zeromagic/doorman/scaling/ScaledApplication.java`
- `src/test/java/io/zeromagic/doorman/scaling/ScaledApplicationRegistryTest.java` — 2 new tests
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 confirmRunning() returns true and transitions state when called from ScaledDown
- [ ] #2 No CompletableFuture is completed when transitioning from ScaledDown (none exists)
- [ ] #3 Existing ScalingUp to Running behavior unchanged
- [ ] #4 Unit tests: onDeploymentReady_whenScaledDown_transitionsToRunning, onDeploymentReady_whenScaledDown_patchesStatusToRunning
- [ ] #5 Subsequent awaitReady() after ScaledDown-to-Running returns a pre-completed future immediately
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
