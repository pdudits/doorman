---
id: TASK-005.06
title: 'ScaledApplicationRegistry: add beginScalingDown(namespace, serviceName)'
status: To Do
assignee: []
created_date: '2026-03-13 11:46'
labels:
  - repository
milestone: m-0
dependencies: []
references:
  - src/main/java/io/zeromagic/doorman/repository/ScaledApplicationRegistry.java
  - src/main/java/io/zeromagic/doorman/repository/ScaledApplication.java
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add `beginScalingDown(String namespace, String serviceName)` to `ScaledApplicationRegistry` \u2014 the entry point used by `IdleDetector` to trigger scale-down of an idle service.

Context: `ScaledApplicationRegistry` is at `src/main/java/io/zeromagic/doorman/repository/ScaledApplicationRegistry.java`. It already has named transition methods following the same pattern. The method delegates to `ScaledApplication.beginScalingDown()` (already implemented) and then patches the ScalingPolicy status via `ScaledPolicyStatusPatcher`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ScaledApplicationRegistry gains a public beginScalingDown(String namespace, String serviceName) method
- [ ] #2 The method looks up the ScaledApplication by (namespace, serviceName), calls app.beginScalingDown(), and patches ScalingPolicy status if the transition succeeded (returned true)
- [ ] #3 If no app is found for that (namespace, serviceName) key, logs a warning and returns
- [ ] #4 Unit tests cover: successful transition, app not found, app already scaled down (no-op)
- [ ] #5 Existing ScaledApplicationRegistryTest tests remain green
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
