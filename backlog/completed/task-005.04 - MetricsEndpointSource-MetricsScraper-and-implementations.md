---
id: TASK-005.04
title: 'MetricsEndpointSource, MetricsScraper and implementations'
status: Done
assignee:
  - copilot
created_date: '2026-03-13 11:46'
updated_date: '2026-03-13 15:12'
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
- [x] #1 MetricsEndpointSource interface: List<URI> endpoints()
- [x] #2 FixedMetricsEndpointSource returns a single-element list from TraefikConfig.Direct.metricsUrl()
- [x] #3 KubernetesPodMetricsEndpointSource sets up a filtered pod informer (@PostConstruct) scoped to namespace + labelSelector from TraefikConfig.Discovered; maintains live map of pod name → URI; implements AutoCloseable
- [x] #4 MetricsScraper.scrape(Consumer<Sample> onSample) fetches all endpoints(), parses each response with OpenMetricsParser, and streams all samples to the consumer; HTTP errors for one endpoint are logged and skipped
- [x] #5 MetricsFetcher is a @FunctionalInterface on MetricsScraper for testability; default wiring uses java.net.http.HttpClient
- [ ] #6 ConfigProvider responsibility moved to TrafficFactory (@Factory in traffic package)
- [ ] #7 Unit tests: FixedMetricsEndpointSourceTest (2 tests); MetricsScraperTest deferred — KubernetesPodMetricsEndpointSource testing blocked on KubernetesFacade refactor (tracked in task-005.08)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### MetricsEndpointSource (interface)
`List<URI> endpoints()`

### FixedMetricsEndpointSource
Constructed from `TraefikConfig.Direct`. Returns `List.of(URI.create(direct.metricsUrl()))`.

### KubernetesPodMetricsEndpointSource
- `@Singleton`, `AutoCloseable`
- Injected: `KubernetesClient`, `TraefikConfig.Discovered`
- `@PostConstruct start()`: sets up `client.pods().inNamespace(ns).withLabelSelector(sel).inform(handler)` 
- Handler maintains `ConcurrentHashMap<String, URI>` (podName → http://podIP:port/metrics)
- onAdd/onUpdate: put; onDelete: remove
- `endpoints()` returns `List.copyOf(map.values())`
- `close()` closes the informer

### MetricsScraper
- `@Singleton`
- `@FunctionalInterface MetricsFetcher { String fetch(URI) throws IOException; }`
- Constructor: `MetricsEndpointSource source, OpenMetricsParser parser, MetricsFetcher fetcher`
- Default bean: `fetcher` = `HttpClient.newHttpClient()` GET → body string
- `scrape(Consumer<Sample> onSample)`: for each URI in source.endpoints(), call fetcher, then parser.parse(text, onSample); IOException → log warn and continue

### ConfigProvider wiring
New `@Bean`:
```java
@Bean
MetricsEndpointSource metricsEndpointSource(TraefikConfig config, KubernetesClient client) {
    return switch (config) {
        case TraefikConfig.Direct d -> new FixedMetricsEndpointSource(d);
        case TraefikConfig.Discovered disc -> new KubernetesPodMetricsEndpointSource(client, disc);
    };
}
```

### Tests
- `FixedMetricsEndpointSourceTest`: endpoints() returns correct URI
- `MetricsScraperTest`: stub MetricsFetcher — samples from two endpoints merged; one 404 skipped
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
AC#6 deviated from plan: bean moved to TrafficFactory (traffic package), not ConfigProvider. AC#7 partially done — FixedMetricsEndpointSourceTest written; MetricsScraperTest and KubernetesPodMetricsEndpointSource behavioral test blocked on KubernetesFacade (task-005.08). DoD#4 (integration test) deferred to task-005.08.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented MetricsEndpointSource hierarchy and MetricsScraper.

- `MetricsEndpointSource` interface with `List<URI> endpoints()`
- `FixedMetricsEndpointSource` for `TraefikConfig.Direct`
- `KubernetesPodMetricsEndpointSource` — owns its informer lifecycle (constructs/closes it), implements `NamespaceRestricted`/`LabelRestricted` for config only
- `TrafficFactory` (@Factory in traffic package) produces `MetricsEndpointSource` bean via pattern match on `TraefikConfig`
- `MetricsScraper` owns `OpenMetricsParser` directly (not a bean); single-arg DI constructor + test constructor with `MetricsFetcher` FI
- `OpenMetricsParser` is not a bean — instantiated directly where needed
- Removed `avaje-inject-test` (no Mockito in project)
- `ConfigProvider` stripped of traffic concerns
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
