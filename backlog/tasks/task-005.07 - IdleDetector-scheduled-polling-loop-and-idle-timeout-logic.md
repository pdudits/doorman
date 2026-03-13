---
id: TASK-005.07
title: 'IdleDetector: scheduled polling loop and idle timeout logic'
status: Done
assignee: []
created_date: '2026-03-13 11:46'
updated_date: '2026-03-13 18:46'
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
- [x] #1 IdleDetector is a @Singleton that starts a polling loop on a virtual thread executor at the configured interval
- [x] #2 On each poll cycle: iterates all registered ScaledApplications; skips any not in Running state; fetches metrics via TraefikMetricsSource; resolves Traefik service label via TraefikServiceNameResolver
- [x] #3 Per-service idle tracking: if counter delta > 0 reset idleSince; if delta == 0 and idleSince is null set idleSince = now; if now - idleSince >= effective idle timeout call registry.beginScalingDown(ns, svc)
- [x] #4 Effective idle timeout = ScalingPolicy.spec.idleTimeout if set, else global --idle-timeout CLI arg
- [x] #5 Counter reset guard: if current counter < previous, treat as traffic seen (reset idleSince) and update baseline
- [x] #6 HTTP/parse errors during a poll cycle are caught, logged as warnings, and the cycle is skipped without crashing
- [x] #7 Only Running services are polled; ScaledDown/ScalingUp/Unknown states are skipped
- [x] #8 Unit tests cover: traffic resets idle timer, no-traffic advances idle timer, threshold fires beginScalingDown, counter reset guard triggers no scale-down, error in one service does not prevent others from being checked
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Design

### DurationParser (new)
Parse "5m", "15s", "1h30m" → Duration. Also accepts ISO-8601 PT... fallback. Lives in `cli` package.

### ScaledApplication.Snapshot (modified)
Add `Duration idleTimeout` — non-null, resolved once at registration time.

### ScaledApplicationRegistry (modified)
Gains `TraefikConfig` constructor arg. In `onAdded`: resolves effective timeout = per-policy or global, passes as `Duration` into Snapshot. IdleDetector reads `snap.idleTimeout()` directly.

### IdleDetector (new @Singleton)
- `@PostConstruct start()` spawns a virtual thread loop; `@PreDestroy stop()` interrupts it
- DI constructor: MetricsScraper, TraefikServiceNameResolver, ScaledApplicationRegistry, TraefikConfig, with a test constructor adding Clock
- `doPoll()`: scrapes metrics into Map<traefikLabel, Double> summing traefik_service_requests_total by service label; calls evaluateAll(counts)
- `evaluateAll(Map<String,Double>)` — package-private; iterates registry.all(), skips non-Running (clearing idle state), calls evaluateOne per service catching/logging exceptions
- `evaluateOne`: resolve label → get counter → compute delta → counter-reset guard (current < last = treat as traffic) → delta>0 = reset idleSince → delta==0 set idleSince if null; fire beginScalingDown when now-idleSince >= snap.idleTimeout()

### IT test
K3sClusterExtension + real facade patcher, Clock.fixed(), call evaluateAll() directly twice then advance clock and verify ScalingPolicy.status.phase == ScalingDown in k3s.
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented IdleDetector with full idle-detection algorithm, DurationParser utility, and per-policy idle timeout support.\n\nNew files:\n- DurationParser — parses "5m"/"15s"/"1h30m"/ISO-8601 → Duration\n- IdleDetector — @Singleton with @PostConstruct/@PreDestroy virtual-thread poll loop; package-private evaluateAll(Map) for direct testing with Clock injection\n- DurationParserTest — 9 unit tests\n- IdleDetectorTest — 8 unit tests (all AC#8 scenarios)\n- IdleDetectorIT — k3s IT: evaluateAll() called directly with Clock.fixed(), verifies ScalingPolicy.status.phase == ScalingDown in k3s API server\n\nModified:\n- ScaledApplication.Snapshot — added Duration idleTimeout (non-null)\n- ScaledApplicationRegistry — new TraefikConfig constructor (DI), test constructor accepts Duration; onAdded resolves effective timeout (per-policy or global); onUpdated also resolves on spec change\n- TraefikServiceNameResolver — promoted to @Singleton with @Inject constructor\n- ScaledApplicationRegistryTest, ScaledApplicationRegistryIT, ScaledApplicationTest — updated for new Snapshot field and registry constructor\n\nmvn verify: all unit tests and 3 IT tests pass.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
