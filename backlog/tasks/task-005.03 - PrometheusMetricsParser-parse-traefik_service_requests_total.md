---
id: TASK-005.03
title: 'PrometheusMetricsParser: parse traefik_service_requests_total'
status: To Do
assignee: []
created_date: '2026-03-13 11:45'
labels:
  - traffic
  - metrics
milestone: m-0
dependencies: []
references:
  - src/main/java/io/zeromagic/doorman/traffic/
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement `PrometheusMetricsParser` \u2014 a stateless parser that reads Traefik Prometheus text-format metrics and extracts the `traefik_service_requests_total` counter for a given service label value.

The Prometheus text format line looks like:
```
traefik_service_requests_total{code=\"200\",method=\"GET\",protocol=\"http\",service=\"default-myapp-80@kubernetes\"} 42
```

The parser must handle multiple label-value pairs per line and must match on the `service` label specifically. Multiple counter lines with different status codes for the same service should be summed.

Context: This is a pure utility with no framework dependencies \u2014 easy to unit test in isolation. Lives in `src/main/java/io/zeromagic/doorman/traffic/`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 PrometheusMetricsParser is a stateless utility class (no dependencies)
- [ ] #2 parseCounter(String metricsText, String serviceLabelValue) returns OptionalDouble — the sum of all matching traefik_service_requests_total counter lines for the given service label value
- [ ] #3 Returns OptionalDouble.empty() if the metric name is absent or no line matches the label
- [ ] #4 Ignores comment lines (starting with #)
- [ ] #5 Handles multiple services in the same metrics blob (returns only the one matching the requested label)
- [ ] #6 Unit tests cover: single matching line, no matching line, multiple services (only correct one returned), malformed/empty input, comment-only input
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
