---
id: TASK-005.07
title: 'IdleDetector: scheduled polling loop and idle timeout logic'
status: To Do
assignee: []
created_date: '2026-03-13 11:46'
labels:
  - traffic
  - metrics
milestone: m-0
dependencies:
  - TASK-005.02
  - TASK-005.03
  - TASK-005.04
  - TASK-005.05
  - TASK-005.06
references:
  - src/main/java/io/zeromagic/doorman/traffic/
  - src/main/java/io/zeromagic/doorman/repository/ScaledApplicationRegistry.java
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement `IdleDetector` \u2014 the scheduled polling component that ties together metrics scraping, Traefik service name resolution, and idle timeout tracking to trigger scale-down.

Depends on: task-005.02 (CLI args), task-005.03 (parser), task-005.04 (metrics source), task-005.05 (name resolver), task-005.06 (registry.beginScalingDown).

Idle detection algorithm per Running service:
1. Resolve Traefik service label via TraefikServiceNameResolver
2. Parse counter from raw metrics text
3. delta = current - lastCounter; update lastCounter
4. If delta > 0 \u2192 reset idleSince to null
5. If delta == 0 and idleSince == null \u2192 idleSince = Instant.now()
6. If delta == 0 and now - idleSince >= idleTimeout \u2192 registry.beginScalingDown(ns, svc)
7. Counter reset guard: if current < lastCounter, treat as traffic seen

Context: Lives in `src/main/java/io/zeromagic/doorman/traffic/`. Uses virtual threads (Thread.ofVirtual or Executors.newVirtualThreadPerTaskExecutor). Poll interval and idle timeout come from DoormanConfig (task-005.02).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 IdleDetector is a @Singleton that starts a polling loop on a virtual thread executor at the configured interval
- [ ] #2 On each poll cycle: iterates all registered ScaledApplications; skips any not in Running state; fetches metrics via TraefikMetricsSource; resolves Traefik service label via TraefikServiceNameResolver
- [ ] #3 Per-service idle tracking: if counter delta > 0 reset idleSince; if delta == 0 and idleSince is null set idleSince = now; if now - idleSince >= effective idle timeout call registry.beginScalingDown(ns, svc)
- [ ] #4 Effective idle timeout = ScalingPolicy.spec.idleTimeout if set, else global --idle-timeout CLI arg
- [ ] #5 Counter reset guard: if current counter < previous, treat as traffic seen (reset idleSince) and update baseline
- [ ] #6 HTTP/parse errors during a poll cycle are caught, logged as warnings, and the cycle is skipped without crashing
- [ ] #7 Only Running services are polled; ScaledDown/ScalingUp/Unknown states are skipped
- [ ] #8 Unit tests cover: traffic resets idle timer, no-traffic advances idle timer, threshold fires beginScalingDown, counter reset guard triggers no scale-down, error in one service does not prevent others from being checked
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
