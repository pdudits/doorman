---
id: TASK-013.06
title: 'E2E: Doorman restart while service is scaled down'
status: To Do
assignee: []
created_date: '2026-03-13 23:40'
updated_date: '2026-03-16 15:21'
labels:
  - e2e
  - system-test
milestone: m-0
dependencies:
  - TASK-013.05
parent_task_id: TASK-013
priority: high
ordinal: 8000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Verify Doorman recovers correctly after a restart while a service is scaled down.

**Test flow:**
1. Scale a service down via DoormanSystemHarness (idle timeout or manual)
2. Verify ScaledDown state, Doorman endpoint registered
3. Stop DoormanSystemHarness (stop informers, proxy, idle detector)
4. Restart DoormanSystemHarness (new instance, same k3s cluster)
5. Verify new instance re-registers Doorman endpoint (registry reads existing ScaledDown status from k3s on startup and calls register())
6. Send request to Traefik — routes to Doorman (re-registered)
7. Trigger scale-up, assert 307 redirect

**Key behavior under test**: On startup, `ScaledApplicationRegistry.onAdded()` for a policy in `ScaledDown` phase must call `registrar.register()` to re-establish the endpoint.

**Key files:**
- new `src/test/java/io/zeromagic/doorman/e2e/RestartWhileDownE2EIT.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 After harness restart, Doorman re-registers its endpoint in k3s
- [ ] #2 Request sent after restart is held and gets 307 after scale-up
- [ ] #3 ScalingPolicy status reflects correct phases throughout
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
