---
id: TASK-005.06
title: 'ScaledApplicationRegistry: add beginScalingDown(namespace, serviceName)'
status: Done
assignee:
  - Copilot
created_date: '2026-03-13 11:46'
updated_date: '2026-03-13 18:20'
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
- [x] #1 ScaledApplicationRegistry gains a public beginScalingDown(String namespace, String serviceName) method
- [x] #2 The method looks up the ScaledApplication by (namespace, serviceName), calls app.beginScalingDown(), and patches ScalingPolicy status if the transition succeeded (returned true)
- [x] #3 If no app is found for that (namespace, serviceName) key, logs a warning and returns
- [x] #4 Unit tests cover: successful transition, app not found, app already scaled down (no-op)
- [x] #5 Existing ScaledApplicationRegistryTest tests remain green
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Add `beginScalingDown(namespace, serviceName)` to `ScaledApplicationRegistry` following the exact pattern of `onRealEndpointsDrained`. Look up via `serviceIndex`, call `app.beginScalingDown()`, patch status if it returned true, warn if not found. Add 3 unit tests to `ScaledApplicationRegistryTest`.
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `beginScalingDown(String namespace, String serviceName)` to `ScaledApplicationRegistry` as the idle detector entry point. Added a `withApp` overload accepting a `notFound` Runnable for the warning case. Added 3 unit tests covering: successful transition (Running→ScalingDown + patch), unknown service (warn + no-op), already ScalingDown (no double patch).

IT test `ScaledApplicationRegistryIT` added: creates a real ScalingPolicy in k3s, calls `beginScalingDown`, and asserts the policy status is patched to `ScalingDown` in the API server. Verified green with `mvn verify`.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
