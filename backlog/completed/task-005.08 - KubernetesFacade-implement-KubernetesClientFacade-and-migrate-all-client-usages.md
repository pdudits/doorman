---
id: TASK-005.08
title: >-
  KubernetesFacade: implement KubernetesClientFacade and migrate all client
  usages
status: Done
assignee: []
created_date: '2026-03-13 15:13'
updated_date: '2026-03-13 15:26'
labels:
  - kubernetes
  - testing
  - refactor
milestone: m-0
dependencies:
  - TASK-005.04
references:
  - src/main/java/io/zeromagic/doorman/kubernetes/KubernetesFacade.java
  - src/main/java/io/zeromagic/doorman/kubernetes/InformerHandler.java
  - >-
    src/main/java/io/zeromagic/doorman/traffic/KubernetesPodMetricsEndpointSource.java
  - src/main/java/io/zeromagic/doorman/traffic/TrafficFactory.java
  - src/main/java/io/zeromagic/doorman/repository/DeploymentInformer.java
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The `KubernetesFacade` interface was sketched out (`src/main/java/io/zeromagic/doorman/kubernetes/KubernetesFacade.java`) but never implemented. It extends `EndpointRegistrar`, `DeploymentStateReader`, `ScalingPolicyStatusPatcher`, `ServiceScaler`, and declares `inform()`.

Currently, `KubernetesClient` is injected directly into `KubernetesDeploymentStateReader`, `KubernetesScalingPolicyStatusPatcher`, `KubernetesPodMetricsEndpointSource`, `TrafficFactory`, and `InformerHandler` (which starts informers for all `ResourceEventHandler` beans discovered via DI).

Goal: Implement `KubernetesFacade` as the single boundary to the Kubernetes API, enabling mock-free testing via a simple `TestKubernetesFacade` that simulates informer events in tests.

Design questions to confirm before coding:
1. Should `inform()` accept a nullable namespace (null = cluster-wide), or two overloads?
2. Does `InformerHandler` get removed (each component calls `facade.inform()` itself) or kept as a central coordinator using the facade?
3. Keep or drop `NamespaceRestricted`/`LabelRestricted` marker interfaces?
4. Is `KubernetesClient` bean still exposed, or internal to `KubernetesFacadeImpl` only?
5. Does `TestKubernetesFacade` live in `src/test` only, or in `src/main` as a named alternative impl?
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 KubernetesClientFacade is a @Singleton in the kubernetes package that absorbs ClientProvider and wraps KubernetesClient; it implements the full KubernetesFacade interface (EndpointRegistrar, DeploymentStateReader, ScalingPolicyStatusPatcher, ServiceScaler, inform())
- [ ] #2 inform(Class<T>, String namespace, String labelSelector, ResourceEventHandler<T>) — namespace and labelSelector nullable for cluster-scoped watches; a default convenience overload inform(Class<T>, ResourceEventHandler<T>) delegates with nulls
- [ ] #3 KubernetesClient is no longer injected outside the kubernetes package; KubernetesClientFacade is the only entry point
- [ ] #4 KubernetesDeploymentStateReader and KubernetesScalingPolicyStatusPatcher are folded into KubernetesClientFacade (or deleted if the facade subsumes them)
- [ ] #5 InformerHandler is removed; each repository informer (DeploymentInformer, EndpointSliceInformer, EndpointsInformer, ServiceInformer, ScalingPolicyInformer) injects KubernetesFacade and starts its own informer in @PostConstruct
- [ ] #6 KubernetesPodMetricsEndpointSource and TrafficFactory accept KubernetesFacade instead of KubernetesClient
- [ ] #7 NamespaceRestricted and LabelRestricted interfaces are removed
- [ ] #8 TestKubernetesFacade in src/test implements KubernetesFacade: inform() captures handlers by resource type and exposes fire*(T resource) helpers; all other methods are no-ops
- [ ] #9 KubernetesPodMetricsEndpointSourceTest uses TestKubernetesFacade to simulate pod add/update/delete events and asserts endpoints() changes accordingly
- [ ] #10 MetricsScraperTest uses a stub MetricsFetcher — samples from two endpoints are merged; one failing endpoint is skipped and does not stop others
- [ ] #11 All existing tests continue to pass
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Design decisions (confirmed with user)

1. `inform()` — single method with nullable namespace/labelSelector; default overload `inform(type, handler)` delegates with nulls
2. `InformerHandler` — **removed**; each component owns its own informer setup via `@PostConstruct`
3. `NamespaceRestricted` / `LabelRestricted` — **dropped**
4. `ClientProvider` — **folded** into `KubernetesClientFacade`; `KubernetesClient` no longer a bean
5. `TestKubernetesFacade` — in `src/test` only

## Implementation steps

### 1. KubernetesClientFacade (@Singleton)
- Absorbs `ClientProvider` constructor logic (reads `Optional<KubernetesConfig>`)
- Implements `KubernetesFacade`: all methods from `EndpointRegistrar`, `DeploymentStateReader`, `ScalingPolicyStatusPatcher`, `ServiceScaler`, plus `inform()`
- `inform(Class<T> type, String namespace, String labelSelector, ResourceEventHandler<T> handler)`:
  - `operation = client.resources(type)`
  - `scoped = namespace != null ? operation.inNamespace(namespace) : operation`
  - `filtered = labelSelector != null ? scoped.withLabelSelector(labelSelector) : scoped`
  - returns `filtered.inform(handler)` (registered in a list for `@PreDestroy`)
- `inform(Class<T> type, ResourceEventHandler<T> handler)` — default method delegating with nulls
- Absorbs `KubernetesDeploymentStateReader.read()` and `KubernetesScalingPolicyStatusPatcher.patch()` implementations
- `@PreDestroy close()` closes all open informer handles

### 2. Delete
- `ClientProvider.java`
- `InformerHandler.java`
- `KubernetesDeploymentStateReader.java`
- `KubernetesScalingPolicyStatusPatcher.java`
- `NamespaceRestricted.java`
- `LabelRestricted.java`

### 3. Update repository informers
Each of `DeploymentInformer`, `EndpointSliceInformer`, `EndpointsInformer`, `ServiceInformer`, `ScalingPolicyInformer` gains:
- Constructor injection of `KubernetesFacade`
- `@PostConstruct start()` calling `facade.inform(ResourceType.class, [namespace,] [label,] this)`

### 4. Update traffic
- `KubernetesPodMetricsEndpointSource(TraefikConfig.Discovered, KubernetesFacade)` — calls `facade.inform(Pod.class, config.namespace(), config.labelSelector(), this)`
- `TrafficFactory.metricsEndpointSource(TraefikConfig, KubernetesFacade)` — passes facade through

### 5. TestKubernetesFacade (src/test)
```java
public class TestKubernetesFacade implements KubernetesFacade {
    private final Map<Class<?>, ResourceEventHandler<?>> handlers = new HashMap<>();

    @Override
    public <T extends HasMetadata> AutoCloseable inform(Class<T> type, String ns, String sel, ResourceEventHandler<T> h) {
        handlers.put(type, h);
        return () -> handlers.remove(type);
    }

    @SuppressWarnings("unchecked")
    public <T extends HasMetadata> void fireAdd(T resource) { ((ResourceEventHandler<T>) handlers.get(resource.getClass())).onAdd(resource); }
    // fireUpdate, fireDelete similarly

    // All other interface methods are no-ops / return empty
}
```

### 6. Tests
- `KubernetesPodMetricsEndpointSourceTest` — uses `TestKubernetesFacade`, fires pod events, asserts `endpoints()` 
- `MetricsScraperTest` — stubs `MetricsFetcher`, two endpoints merged, one failing skipped
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## KubernetesFacade Refactor

Replaced the fragmented Kubernetes client plumbing with a single `KubernetesFacade` interface.

**Deleted:**
- `ClientProvider`, `InformerHandler`, `KubernetesDeploymentStateReader`, `KubernetesScalingPolicyStatusPatcher`, `NamespaceRestricted`, `LabelRestricted` (from kubernetes package)
- `NoOpStubs` (from repository package)

**Created:**
- `KubernetesClientFacade` — single `@Singleton` impl of `KubernetesFacade`; owns the `KubernetesClient` lifecycle (`@PreDestroy`)
- `TestKubernetesFacade` (test-only) — captures informer handlers, exposes `fireAdd/Update/Delete` helpers; all other methods are no-ops

**Refactored:**
- All 5 repository informers (`DeploymentInformer`, `EndpointSliceInformer`, `EndpointsInformer`, `ServiceInformer`, `ScalingPolicyInformer`) — inject `KubernetesFacade`, self-register via `@PostConstruct`
- `KubernetesPodMetricsEndpointSource` — uses `KubernetesFacade.inform()` instead of raw `KubernetesClient`; implements `ResourceEventHandler<Pod>` directly
- `TrafficFactory` — changed from `KubernetesClient` to `KubernetesFacade`
- `ConfigProvider` — stripped of traffic beans (moved to `TrafficFactory`)
- `MetricsScraper` — test constructor for injecting `MetricsFetcher` stub

**New tests:**
- `KubernetesPodMetricsEndpointSourceTest` — 6 cases covering add/update/delete/multi-pod/no-IP using `TestKubernetesFacade`
- `MetricsScraperTest` — 3 cases covering multi-endpoint scrape, skip-on-error, empty source

All 75 tests pass.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
