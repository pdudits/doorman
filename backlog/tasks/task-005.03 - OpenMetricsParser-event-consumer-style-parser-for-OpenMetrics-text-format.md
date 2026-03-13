---
id: TASK-005.03
title: 'OpenMetricsParser: event/consumer-style parser for OpenMetrics text format'
status: Done
assignee:
  - copilot
created_date: '2026-03-13 11:45'
updated_date: '2026-03-13 12:08'
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
- [x] #1 OpenMetricsParser is a stateless utility class (no dependencies)
- [x] #2 parse(String text, Consumer<Sample> onSample) iterates data lines and calls onSample for each; callers filter what they need — no map built internally
- [x] #3 Sample is a record: (String metricName, Map<String, String> labels, double value)
- [x] #4 Comment lines (starting with #) and blank lines are skipped
- [x] #5 Malformed lines (unparseable value, missing braces) are silently skipped
- [x] #6 Unit tests cover: single matching line, multiple samples for same metric (different labels), caller filtering by label value, comment-only input, malformed line skipped, empty input
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Design
Event/consumer style — caller drives filtering, parser just emits samples:
```java
parser.parse(metricsText, sample -> {
    if ("traefik_service_requests_total".equals(sample.metricName())
            && serviceName.equals(sample.labels().get("service"))) {
        counter[0] += sample.value();
    }
});
```

### `Sample` record
`record Sample(String metricName, Map<String, String> labels, double value)`

### Parse algorithm (per non-comment, non-blank line)
1. Find `{` — metric name is everything before it
2. Extract label string between `{ }`, parse as key="value" pairs into Map
3. Value is the token after `}`
4. Call `onSample(new Sample(name, labels, value))`
5. On any parse error (NumberFormatException, bad structure) — skip line silently

Lines without labels (no `{`) are also supported: name is full token before space, labels = empty map.

### Files
- `src/main/java/io/zeromagic/doorman/traffic/OpenMetricsParser.java`
- `src/test/java/io/zeromagic/doorman/traffic/OpenMetricsParserTest.java`
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Created `OpenMetricsParser` with event/consumer-style API: `parse(String text, Consumer<Sample> onSample)`. `Sample` is a record `(String metricName, Map<String, String> labels, double value)`. The parser skips `#` comments, blank lines, and silently drops malformed lines. Label parsing handles escaped quotes. No internal map is built — callers filter by registering a lambda.\n\nAdded `OpenMetricsParserTest` with 14 tests covering: labelled and unlabelled samples, float values, optional timestamp token, caller-side filtering/summing, comment/blank/empty/null skipping, malformed value skipping, unclosed brace skipping, escaped quote in label, empty label set. 64/64 tests passing.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
