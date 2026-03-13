---
id: TASK-005
title: Traefik Prometheus metrics scraper and idle detection
status: Done
assignee: []
created_date: '2026-03-12 11:26'
updated_date: '2026-03-13 18:47'
labels:
  - traffic
  - traefik
  - metrics
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the traffic monitoring component in the `traffic` package. This periodically scrapes the Traefik Prometheus metrics endpoint and determines which managed services are idle.

Traefik exposes per-service request counters at `/metrics`. The relevant metric is `traefik_service_requests_total` labeled with `service=<traefikServiceName>`. By comparing the counter value between two polls, Doorman determines if any traffic has arrived.

The idle timeout window (from ScalingPolicy spec) is tracked per-service. When a service crosses the idle threshold, this component signals the scaling subsystem to scale the deployment to zero.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Polls Traefik metrics on a configurable interval (default 15s) via --metrics-poll-interval CLI arg
- [x] #2 Parses traefik_service_requests_total counter; detects per-service traffic delta between polls
- [x] #3 Traefik service name derived as namespace-serviceName-port@kubernetes; port read from Kubernetes Ingress named in ScalingPolicy.spec.ingressName
- [x] #4 ScalingPolicy.spec gains ingressName (required for port resolution) and optional idleTimeout fields; CRD YAML updated
- [x] #5 Idle detection triggers registry.beginScalingDown() when zero new requests for the configured idleTimeout; idleTimeout defaults to global --idle-timeout CLI arg (default 5m)
- [ ] #6 TraefikMetricsSource interface with two impls: UrlTraefikMetricsSource (--traefik-metrics-url) and KubernetesPodTraefikMetricsSource (--traefik-namespace + --traefik-label-selector + --traefik-metrics-port default 9100)
- [x] #7 Counter reset (Traefik restart) detected by counter < previous; treated as traffic seen to avoid false-positive scale-down
- [x] #8 Only Running services are polled; other states are skipped in each poll cycle
- [x] #9 HTTP/parse errors are logged as warnings and the cycle is skipped (no crash)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
# Task-005 Implementation Plan

## Design

### Traefik source selection (CLI)
- `--traefik-metrics-url` → `UrlTraefikMetricsSource`
- `--traefik-namespace` + `--traefik-label-selector` → `KubernetesPodTraefikMetricsSource`

### Traefik service name
Fixed pattern: `{namespace}-{serviceName}-{port}@kubernetes`
Port resolved from the Kubernetes Ingress named `ScalingPolicy.spec.ingressName`.
`TraefikServiceNameResolver` caches results; refreshes on `onUpdated`.

### Idle detection
`IdleDetector` runs a scheduled polling loop.
Per service (in Running state only):
1. Fetch + parse metrics
2. Delta = current counter - lastCounter
3. If delta > 0 → reset idleSince
4. Else if idleSince == null → idleSince = now
5. Else if now - idleSince >= idleTimeout → call registry.beginScalingDown(ns, svc)

Counter reset guard: if current < last, treat as traffic seen, update lastCounter.

## Files
- `ScalingPolicySpec.java` — add ingressName, idleTimeout
- `scalingpolicy-crd.yaml` — add fields
- `CliArgs.java` / `DoormanConfig.java` — add 6 new args
- `traffic/TraefikMetricsSource.java` — interface
- `traffic/UrlTraefikMetricsSource.java`
- `traffic/KubernetesPodTraefikMetricsSource.java`
- `traffic/PrometheusMetricsParser.java`
- `traffic/TraefikServiceNameResolver.java`
- `traffic/IdleDetector.java`
- `ScaledApplicationRegistry.java` — add beginScalingDown(ns, svc)

## Tests
- `PrometheusMetricsParserTest`
- `TraefikServiceNameResolverTest`
- `IdleDetectorTest`
<!-- SECTION:PLAN:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
- [ ] #6 AC#6 KubernetesPodTraefikMetricsSource deferred to TASK-005.08 or later — URL-based source is implemented and sufficient for milestone m-0
<!-- DOD:END -->
