---
id: TASK-013.03
title: 'Fix: ScaledDown to Running direct transition on manual scale-up'
status: To Do
assignee: []
created_date: '2026-03-13 23:38'
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
Fix a state machine gap: when `onDeploymentChanged` fires with `readyReplicas >= 1` and Doorman is in `ScaledDown` state (no pending `awaitReady` call — e.g. manual scale-up or Doorman restart), `confirmRunning()` currently returns false and the state stays `ScaledDown`.

**The bug**: The next incoming proxy request calls `awaitReady()` on what should be a Running service, transitions `ScaledDown → ScalingUp`, and issues `scaleUp()` on an already-running deployment. This is incorrect.

**Root cause**: `ScaledApplication.confirmRunning()` only handles `ScalingUp → Running`. `ScaledDown` is not handled.

**Fix**: Extend `confirmRunning()` to also transition `ScaledDown → Running`:

```java
next = switch (old) {
    case ServiceState.ScalingUp ignored -> new ServiceState.Running();
    case ServiceState.ScaledDown ignored -> new ServiceState.Running();  // add this
    default -> old;
};
// ...
if (old instanceof ServiceState.ScalingUp(var future)) {
    future.complete(null);
    return true;
}
if (old instanceof ServiceState.ScaledDown) {
    return true;  // no future to complete, state transitioned
}
return false;
```

`onDeploymentChanged` already calls `registrar.deregister()` before `confirmRunning()` — ordering is preserved.

**Unit tests to add in `ScaledApplicationRegistryTest`:**
- `onDeploymentReady_whenScaledDown_transitionsToRunning` — deployment ready event with no prior awaitReady call; verify subsequent awaitReady returns completed future
- `onDeploymentReady_whenScaledDown_patches_status` — verify patchStatus called with Running phase

**Key files:**
- `src/main/java/io/zeromagic/doorman/scaling/ScaledApplication.java` — extend `confirmRunning()`
- `src/test/java/io/zeromagic/doorman/scaling/ScaledApplicationRegistryTest.java` — 2 new tests
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 When onDeploymentChanged fires with readyReplicas >= 1 and state is ScaledDown, state transitions to Running
- [ ] #2 confirmRunning() returns true for both ScalingUp and ScaledDown inputs
- [ ] #3 No CompletableFuture is completed when transitioning from ScaledDown (no future exists)
- [ ] #4 Existing ScalingUp → Running behavior unchanged
- [ ] #5 Deregister is still called before state transition in onDeploymentChanged
- [ ] #6 New unit tests added for the ScaledDown → Running path
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
