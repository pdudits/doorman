---
id: TASK-007.01
title: scale-up-timeout CLI arg + DoormanConfig
status: Done
assignee: []
created_date: '2026-03-13 20:26'
updated_date: '2026-03-13 23:17'
labels:
  - proxy
  - cli
milestone: m-0
dependencies: []
parent_task_id: TASK-007
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Simple prerequisite for the proxy server.

**Scope:**
1. Add `--scale-up-timeout` (String, default `"60s"`) to `CliArgs.java`
2. Parse via `DurationParser` in `ConfigProvider.java`
3. Add `scaleUpTimeout` (`Duration`) field to `DoormanConfig` record — breaking change, update all construction sites

**Key files:**
- `src/main/java/io/zeromagic/doorman/cli/CliArgs.java`
- `src/main/java/io/zeromagic/doorman/cli/DoormanConfig.java`
- `src/main/java/io/zeromagic/doorman/cli/ConfigProvider.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 --scale-up-timeout CLI arg added to CliArgs (String, default "60s")
- [x] #2 DoormanConfig record has scaleUpTimeout() returning Duration
- [x] #3 ConfigProvider parses the arg via DurationParser and passes it to DoormanConfig
- [x] #4 Code compiles; all existing tests pass
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Decisions

**picocli `defaultValue` vs field initializer**: `@Option(defaultValue = "60s")` only applies when picocli parses arguments. Direct `new CliArgs()` construction in tests gets `null`. Fix: add a Java field initializer alongside the annotation (`String scaleUpTimeout = "60s"`). This became a recurring pattern applied to all subsequently added CLI args (`--propagation-delay`).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `--scale-up-timeout` (String, default "60s") to `CliArgs.java`. Added `scaleUpTimeout` (`Duration`) as third component of `DoormanConfig` record. `ConfigProvider` parses it via same-package `DurationParser.parse()`. Updated two test construction sites: `KubernetesClientFacadeAccessor.DUMMY_CONFIG` and `EndpointRegistrarIT.DOORMAN_CONFIG`. No new tests needed — pure config wiring. `mvn compile test-compile` clean.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
