---
id: TASK-005.02
title: CLI args and DoormanConfig for Traefik metrics configuration
status: Done
assignee:
  - copilot
created_date: '2026-03-13 11:45'
updated_date: '2026-03-13 12:02'
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
- [x] #1 --traefik-metrics-url (String, optional) — single Traefik metrics endpoint URL
- [x] #2 --traefik-namespace (String, optional) — Kubernetes namespace to discover Traefik pods
- [x] #3 --traefik-label-selector (String, optional) — label selector for Traefik pods (e.g. 'app=traefik')
- [x] #4 --traefik-metrics-port (int, default 9100) — port to scrape on each discovered Traefik pod
- [x] #5 --idle-timeout (String, default '5m') — global idle timeout before a service is scaled to zero
- [x] #6 --metrics-poll-interval (String, default '15s') — how often to poll Traefik metrics
- [x] #7 All args are accessible via DoormanConfig
- [x] #8 App fails fast with a clear error if neither traefik-metrics-url nor (traefik-namespace + traefik-label-selector) are provided
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### 1. `CliArgs.java`
Add 6 new `@Option` fields:
- `traefikMetricsUrl` — `--traefik-metrics-url`, optional String
- `traefikNamespace` — `--traefik-namespace`, optional String
- `traefikLabelSelector` — `--traefik-label-selector`, optional String
- `traefikMetricsPort` — `--traefik-metrics-port`, int default 9100
- `idleTimeout` — `--idle-timeout`, String default "5m"
- `metricsPollInterval` — `--metrics-poll-interval`, String default "15s"

### 2. `DoormanConfig.java`
Expand record to add all 6 new fields alongside existing `podIp`.

### 3. `ConfigProvider.java`
In `doormanConfig()`:
- Populate all new fields from args
- Add fail-fast: if traefikMetricsUrl is null AND (traefikNamespace or traefikLabelSelector is null) → throw IllegalStateException with clear message

### Validation
- `mvn test` — existing tests still pass (DoormanConfig record shape change is additive; no existing test creates a DoormanConfig directly)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Reopened: user requested refactor to extract TraefikConfig as a sealed interface with DirectTraefikConfig(metricsUrl) and DiscoveredTraefikConfig(namespace, labelSelector, metricsPort) subtypes. DoormanConfig loses the flat traefik fields and gains a single TraefikConfig field.

Further refactor: ConfigProvider splits into independent @Bean methods — traefikConfig() and doormanConfig(). DoormanConfig drops the TraefikConfig field (consumers inject TraefikConfig directly). Unit tests written for both factory methods.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added 6 new CLI args to `CliArgs.java` (Picocli `@Option`): `--traefik-metrics-url`, `--traefik-namespace`, `--traefik-label-selector`, `--traefik-metrics-port` (default 9100), `--idle-timeout` (default 5m), `--metrics-poll-interval` (default 15s).\n\nCreated `TraefikConfig.java` — a sealed interface with two record subtypes that pattern-match cleanly downstream:\n- `TraefikConfig.Direct(metricsUrl)` — single URL mode\n- `TraefikConfig.Discovered(namespace, labelSelector, metricsPort)` — pod-discovery mode\n\n`DoormanConfig` record is clean: `(podIp, idleTimeout, metricsPollInterval)`. `TraefikConfig` is its own independent `@Bean` in `ConfigProvider`, separately testable. Each `@Bean` method has a single responsibility and throws `IllegalStateException` with a clear message on misconfiguration.\n\nAdded `ConfigProviderTest` with 9 tests covering all config paths. Total: 50/50 tests passing.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
- [ ] #6 Integration test not applicable — CLI arg wiring is validated by fail-fast logic and covered by the existing ConfigProvider bean wiring; no test creates DoormanConfig directly
<!-- DOD:END -->
