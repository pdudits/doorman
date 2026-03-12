---
id: TASK-005
title: Traefik Prometheus metrics scraper and idle detection
status: To Do
assignee: []
created_date: '2026-03-12 11:26'
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
- [ ] #1 Polls the Traefik Prometheus /metrics endpoint on a configurable interval (default: 15s)
- [ ] #2 Parses the `traefik_service_requests_total` (or `traefik_service_request_duration_seconds_count`) counter to detect per-service request rate
- [ ] #3 Identifies the correct Traefik service by matching `service` label against `ScalingPolicy.spec.traefikServiceName`
- [ ] #4 Uses a sliding window or delta between two polls to determine if a service is idle (no new requests since last poll)
- [ ] #5 Idle detection triggers the scale-down flow when a service has received zero new requests for the configured `idleTimeout`
- [ ] #6 The Traefik metrics URL is configurable via CLI arg `--traefik-metrics-url` (default: `http://traefik.kube-system.svc:9100/metrics`)
- [ ] #7 Uses `java.net.http.HttpClient` for the HTTP request
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
