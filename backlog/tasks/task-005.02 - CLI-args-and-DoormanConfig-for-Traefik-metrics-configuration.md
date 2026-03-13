---
id: TASK-005.02
title: CLI args and DoormanConfig for Traefik metrics configuration
status: To Do
assignee: []
created_date: '2026-03-13 11:45'
labels:
  - config
  - traffic
milestone: m-0
dependencies: []
references:
  - src/main/java/io/zeromagic/doorman/config/
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add all new CLI arguments needed by the traffic subsystem to `CliArgs.java` (Picocli) and expose them via `DoormanConfig.java` (avaje config).

Context: The project uses Picocli for CLI parsing and avaje for DI/config. `CliArgs` is in `src/main/java/io/zeromagic/doorman/config/`. New args must be wired as avaje config values so downstream beans can receive them.

Two mutually exclusive Traefik source modes exist:
- URL mode: `--traefik-metrics-url` (single endpoint)
- Pod-discovery mode: `--traefik-namespace` + `--traefik-label-selector` + `--traefik-metrics-port` (default 9100)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 --traefik-metrics-url (String, optional) — single Traefik metrics endpoint URL
- [ ] #2 --traefik-namespace (String, optional) — Kubernetes namespace to discover Traefik pods
- [ ] #3 --traefik-label-selector (String, optional) — label selector for Traefik pods (e.g. 'app=traefik')
- [ ] #4 --traefik-metrics-port (int, default 9100) — port to scrape on each discovered Traefik pod
- [ ] #5 --idle-timeout (String, default '5m') — global idle timeout before a service is scaled to zero
- [ ] #6 --metrics-poll-interval (String, default '15s') — how often to poll Traefik metrics
- [ ] #7 All args are accessible via DoormanConfig
- [ ] #8 App fails fast with a clear error if neither traefik-metrics-url nor (traefik-namespace + traefik-label-selector) are provided
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
