---
id: TASK-013.03
title: 'Feature: reset idle timer on new deployment rollout'
status: To Do
assignee: []
created_date: '2026-03-13 23:38'
labels:
  - feature
  - scaling
  - idle-detection
milestone: m-0
dependencies: []
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement idle timer reset when a new Deployment rollout starts. Without this, a freshly deployed service can be scaled to 0 immediately if it happens to have no in-flight requests during the rollout.

**Trigger condition**: `metadata.generation > status.observedGeneration` on a `DeploymentChanged` event means a rollout is in progress.

**Design decision**: `ScaledApplicationRegistry.onDeploymentChanged()` already receives deployment events and has the `(namespace, serviceName)` key. Add a `resetIdleTimer(namespace, serviceName)` callback to `IdleDetector` (or a new `IdleTimerEvents` interface). `ScaledApplicationRegistry` fires this when it detects a generation mismatch. This avoids making `IdleDetector` implement `DeploymentEvents` directly (which would require it to duplicate the deployment→service key mapping).

**Implementation:**
1. `IdleDetector`: add `resetTimer(namespace, serviceName)` method — sets `idleSince = null` in the state map for that key
2. `ScaledApplicationRegistry`: inject `IdleDetector` (or `IdleTimerReset` interface). In `onDeploymentChanged()`, if `generation > observedGeneration`, call `idleDetector.resetTimer(snap.namespace(), snap.serviceName())`

**Circular dependency check**: `IdleDetector` calls `registry.beginScalingDown()`. `ScaledApplicationRegistry` would call `idleDetector.resetTimer()`. This is a real cycle — must be broken.
- Break with a narrow interface: `IdleTimerReset { void resetTimer(String namespace, String serviceName); }`
- `IdleDetector` implements both `ScalingPolicyEvents` and `IdleTimerReset`
- `ScaledApplicationRegistry` injects `IdleTimerReset` (not `IdleDetector`)
- avaje will still wire it correctly (single implementation)

**Key files:**
- new `src/main/java/io/zeromagic/doorman/traffic/IdleTimerReset.java` (interface)
- `src/main/java/io/zeromagic/doorman/traffic/IdleDetector.java` — add `resetTimer()`, implement `IdleTimerReset`
- `src/main/java/io/zeromagic/doorman/scaling/ScaledApplicationRegistry.java` — inject `IdleTimerReset`, call on generation mismatch
- `src/test/java/io/zeromagic/doorman/traffic/IdleDetectorTest.java` — test reset behavior
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Deploying a new image (generation change) resets the idle timer for that service
- [ ] #2 If a service is at the idle timeout boundary, a new rollout prevents scale-down until the rollout completes
- [ ] #3 Idle timer resumes normally once the rollout is complete (observedGeneration == generation)
- [ ] #4 Existing idle detection behavior for stable deployments is unchanged
- [ ] #5 Unit tests added for the reset behavior
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
