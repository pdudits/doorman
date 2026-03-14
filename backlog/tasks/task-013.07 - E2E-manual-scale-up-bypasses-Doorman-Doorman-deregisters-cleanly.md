---
id: TASK-013.07
title: 'E2E: manual scale-up bypasses Doorman, Doorman deregisters cleanly'
status: To Do
assignee: []
created_date: '2026-03-13 23:40'
labels:
  - e2e
  - system-test
milestone: m-0
dependencies:
  - TASK-013.04
  - TASK-013.05
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Verify Doorman correctly handles a manually scaled-up deployment: deregisters its endpoint and transitions to Running without issuing a redundant scaleUp call.

**Test flow:**
1. Scale service down (Doorman endpoint registered, state = ScaledDown)
2. Manually patch `spec.replicas = 1` on the Deployment via Fabric8 (bypassing Doorman's scaleUp)
3. Wait for nginx pod to become ready (onDeploymentChanged fires with readyReplicas = 1)
4. Assert Doorman deregistered its endpoint (EndpointSlice no longer contains Doorman's IP)
5. Assert Doorman state = Running (TASK-013.04 fix applied)
6. Assert `scaleUp()` was NOT called a second time (verify via spy/counter in harness)
7. Send request to `http://nginx.test:{traefikHttpPort}/` — should go directly to nginx, returning 200 (not 307)

**Key files:**
- new `src/test/java/io/zeromagic/doorman/e2e/ManualScaleUpE2EIT.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Manually patching replicas to 1 while ScaledDown causes Doorman to deregister its endpoint
- [ ] #2 Doorman state transitions to Running (TASK-013.04 fix)
- [ ] #3 Subsequent requests to Traefik go directly to nginx (not Doorman)
- [ ] #4 Idle detection resumes normally and can scale down again after traffic stops
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
