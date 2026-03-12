---
id: TASK-009
title: 'CLI and configuration: expand options for all components'
status: To Do
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-12 11:33'
labels:
  - cli
  - configuration
milestone: m-0
dependencies: []
priority: medium
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
- [ ] #1 All configuration is available via `CliArgs` using picocli `@Command` and `@Option`/`@Parameters`
- [ ] #2 Options: `--kube-context` (existing), `--proxy-port` (int, default 8080), `--traefik-metrics-url` (string), `--metrics-poll-interval` (duration, default 15s), `--scale-up-timeout` (duration, default 60s), `--pod-ip` (string, optional — overrides MY_POD_IP env var)
- [ ] #3 All options can also be set via environment variables using avaje-config (`PROXY_PORT`, `TRAEFIK_METRICS_URL`, etc.) with CLI args taking precedence
- [ ] #4 Running `--help` prints usage with descriptions for all options
- [ ] #5 Startup logs a summary of effective configuration
- [ ] #6 Startup fails with a clear error message if neither `--pod-ip` nor `MY_POD_IP` is resolvable
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
