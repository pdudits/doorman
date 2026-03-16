---
id: TASK-013.07
title: >-
  E2E edge cases: deployment rollout resets timer, concurrent holds, scale-up
  timeout
status: To Do
assignee: []
created_date: '2026-03-13 23:40'
updated_date: '2026-03-16 15:21'
labels:
  - e2e
  - system-test
milestone: m-0
dependencies:
  - TASK-013.03
  - TASK-013.05
parent_task_id: TASK-013
priority: high
ordinal: 9000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
E2E tests for edge cases that complement the happy path.

**Scenario A — New deployment resets idle timer (requires TASK-013.03):**
1. Create service with 5s idle timeout
2. Wait 4s (near timeout boundary) then apply a new Deployment image (increments generation)
3. Verify scale-down does NOT happen during rollout
4. Wait for rollout to complete (observedGeneration == generation)
5. Wait another 5s with no traffic — verify scale-down happens now

**Scenario B — Concurrent requests all get 307:**
1. Scale service to 0 (ScaledDown)
2. Fire N concurrent HTTP requests to Traefik (e.g., N = 10, via virtual threads)
3. All requests held simultaneously
4. Trigger deployment ready event
5. Assert all N responses are HTTP 307 with correct Location

**Scenario C — Scale-up timeout returns 503 to all held requests:**
1. Scale service to 0 (ScaledDown)
2. Configure harness with very short scaleUpTimeout (e.g., 500ms)
3. Send request — held
4. Do NOT complete the deployment ready event within timeout
5. Assert HTTP 503

**Key files:**
- new `src/test/java/io/zeromagic/doorman/e2e/EdgeCasesE2EIT.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 New deployment rollout resets idle timer (no scale-down during rollout)
- [ ] #2 After rollout completes, idle detection resumes and can trigger scale-down normally
- [ ] #3 N concurrent requests while ScaledDown all receive 307 (not partial failures)
- [ ] #4 When scale-up timeout expires, all held requests receive 503
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
