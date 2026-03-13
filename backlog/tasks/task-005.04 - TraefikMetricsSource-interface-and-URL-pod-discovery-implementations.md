---
id: TASK-005.04
title: TraefikMetricsSource interface and URL + pod-discovery implementations
status: To Do
assignee: []
created_date: '2026-03-13 11:46'
labels:
  - traffic
  - metrics
milestone: m-0
dependencies:
  - TASK-005.02
references:
  - src/main/java/io/zeromagic/doorman/traffic/
  - src/main/java/io/zeromagic/doorman/config/
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the `TraefikMetricsSource` interface and its two implementations that fetch raw Prometheus metrics text from Traefik.

Depends on task-005.02 for CLI args (URL, namespace, label selector, port).

Two modes:
- **URL mode** (`--traefik-metrics-url`): Single HTTP GET to the given URL
- **Pod-discovery mode** (`--traefik-namespace` + `--traefik-label-selector` + `--traefik-metrics-port`): Discover Traefik pods via Kubernetes API, GET metrics from each pod IP, concatenate results

The raw metrics text is then parsed by `PrometheusMetricsParser` (task-005.03).

Context: Lives in `src/main/java/io/zeromagic/doorman/traffic/`. Uses `java.net.http.HttpClient` and Fabric8 `KubernetesClient` for pod discovery.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TraefikMetricsSource interface with single method: String fetchMetrics() throws IOException
- [ ] #2 UrlTraefikMetricsSource: constructs java.net.http.HttpClient GET to the configured URL, returns response body as String, throws IOException on non-200 or network error
- [ ] #3 KubernetesPodTraefikMetricsSource: lists pods in configured namespace matching label selector, GETs http://{podIP}:{metricsPort}/metrics for each pod, concatenates all responses (parser will sum across them)
- [ ] #4 Source selection wired in ConfigProvider/avaje DI: if traefik-metrics-url is set use URL impl; else use pod-discovery impl
- [ ] #5 Both impls throw IOException rather than crashing the poll loop (errors handled by caller)
- [ ] #6 Unit tests for UrlTraefikMetricsSource with a fake HttpClient or WireMock-style stub (no real network calls)
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
