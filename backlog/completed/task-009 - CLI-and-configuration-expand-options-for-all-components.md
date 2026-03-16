---
id: TASK-009
title: 'CLI and configuration: expand options for all components'
status: Done
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-16 22:10'
labels:
  - cli
  - configuration
milestone: m-0
dependencies: []
priority: medium
ordinal: 1000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Expand the CLI configuration in the `cli` package to cover all configuration options needed by the full application. Currently only `--kube-context` exists.

New options needed:
- `--proxy-port` (default 8080): port for the holding proxy server
- `--traefik-metrics-url`: full URL to Traefik's Prometheus metrics endpoint
- `--metrics-poll-interval` (default 15s): how often to scrape Traefik metrics  
- `--scale-up-timeout` (default 60s): max wait time for a deployment to become ready
- `--pod-ip`: Doorman's own IP to register as an endpoint; overrides `MY_POD_IP` env var (useful when running from a laptop with a tunnel into the cluster)

Also integrate avaje-config for environment variable fallbacks so the same settings work both in development (CLI flags) and in Kubernetes (env vars in the Deployment manifest).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All configuration is available via `CliArgs` using picocli `@Command` and `@Option`/`@Parameters`
- [x] #2 Options: `--kube-context` (existing), `--proxy-port` (int, default 8080), `--traefik-metrics-url` (string), `--metrics-poll-interval` (duration, default 15s), `--scale-up-timeout` (duration, default 60s), `--pod-ip` (string, optional — overrides MY_POD_IP env var)
- [x] #3 All options can also be set via environment variables using avaje-config (`PROXY_PORT`, `TRAEFIK_METRICS_URL`, etc.) with CLI args taking precedence
- [x] #4 Running `--help` prints usage with descriptions for all options
- [x] #5 Startup logs a summary of effective configuration
- [x] #6 Startup fails with a clear error message if neither `--pod-ip` nor `MY_POD_IP` is resolvable
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
Three gaps to close:

**AC3 — Env var fallbacks via picocli** (not avaje-config):
- Add `defaultValue = "${ENV_VAR:-}"` to every @Option in CliArgs.java
- Env var names: POD_IP, DOORMAN_PROXY_PORT, TRAEFIK_METRICS_URL, TRAEFIK_NAMESPACE, TRAEFIK_LABEL_SELECTOR, TRAEFIK_METRICS_PORT, IDLE_TIMEOUT, METRICS_POLL_INTERVAL, SCALE_UP_TIMEOUT, PROPAGATION_DELAY, KUBE_CONTEXT
- Remove manual System.getenv("POD_IP") from ConfigProvider (picocli handles it)

**AC4 — --kube-context description**:
- Add description = "Kubernetes context name to use (default: current context)."

**AC5 — Startup log**:
- Add SLF4J LOGGER to ConfigProvider
- Log effective config after building DoormanConfig and TraefikConfig

Files: CliArgs.java, ConfigProvider.java
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC3: Used picocli native defaultValue = "${ENV_VAR:-default}" syntax instead of avaje-config — cleaner and no extra dependency. Removed manual System.getenv("POD_IP") from ConfigProvider. AC4: Added description to --kube-context. AC5: Added SLF4J startup logging to ConfigProvider after building DoormanConfig and TraefikConfig. Field initializers kept on scaleUpTimeout/propagationDelay so tests that construct CliArgs directly still work.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## TASK-009: CLI and configuration

All 6 ACs complete. Three gaps closed:

**AC3 — Env var fallbacks**: Added picocli `defaultValue = \"${ENV_VAR:-default}\"` to every `@Option` in `CliArgs.java`. Env vars: `POD_IP`, `DOORMAN_PROXY_PORT`, `TRAEFIK_METRICS_URL`, `TRAEFIK_NAMESPACE`, `TRAEFIK_LABEL_SELECTOR`, `TRAEFIK_METRICS_PORT`, `IDLE_TIMEOUT`, `METRICS_POLL_INTERVAL`, `SCALE_UP_TIMEOUT`, `PROPAGATION_DELAY`, `KUBE_CONTEXT`. Removed manual `System.getenv(\"POD_IP\")` from `ConfigProvider`.

**AC4 — --kube-context description**: Added description text.

**AC5 — Startup log**: Added SLF4J logger to `ConfigProvider`; logs effective `DoormanConfig` and `TraefikConfig` values at INFO level after construction.

All unit tests pass. `mvn verify -DskipTests` clean.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
