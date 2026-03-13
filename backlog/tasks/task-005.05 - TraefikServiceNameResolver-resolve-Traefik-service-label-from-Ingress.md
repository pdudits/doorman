---
id: TASK-005.05
title: 'TraefikServiceNameResolver: resolve Traefik service label from Ingress'
status: Done
assignee:
  - Copilot
created_date: '2026-03-13 11:46'
updated_date: '2026-03-13 17:22'
labels:
  - traffic
  - kubernetes
milestone: m-0
dependencies:
  - TASK-005.01
references:
  - src/main/java/io/zeromagic/doorman/traffic/
  - src/main/java/io/zeromagic/doorman/crd/
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement `TraefikServiceNameResolver` \u2014 resolves the Traefik internal service label string from a Kubernetes Ingress, needed by `IdleDetector` to look up the right counter in Prometheus metrics.

Traefik internally names a service as `{namespace}-{serviceName}-{port}@kubernetes`. The port comes from the backend definition in the Kubernetes Ingress named `ScalingPolicy.spec.ingressName`.

Depends on task-005.01 (ingressName field on ScalingPolicySpec).

Context: Lives in `src/main/java/io/zeromagic/doorman/traffic/`. Uses Fabric8 `KubernetesClient` to fetch the Ingress object. The Fabric8 model for Ingress is in `io.fabric8.kubernetes.api.model.networking.v1`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 TraefikServiceNameResolver resolves the Traefik internal service label for a given (namespace, serviceName) pair
- [x] #2 Resolution algorithm: GET the Kubernetes Ingress named ScalingPolicy.spec.ingressName in the same namespace; find the backend port for the service; build the label as '{namespace}-{serviceName}-{port}@kubernetes'
- [x] #3 Results are cached after first successful lookup (avoids repeated Ingress API calls)
- [x] #4 Cache is invalidated when the associated ScalingPolicy is updated (resolver implements onUpdated for ScalingPolicy events, or accepts an explicit invalidate call)
- [x] #5 Returns Optional.empty() and logs a warning if the Ingress is not found or does not reference the service
- [x] #6 Unit tests with fake/stub Ingress objects (no real Kubernetes cluster required); covers: happy path, ingress not found, service not in ingress rules
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Overview
Implement `TraefikServiceNameResolver` in `src/main/java/io/zeromagic/doorman/traffic/` that resolves Traefik's internal service label `{namespace}-{serviceName}-{port}@kubernetes` by fetching the Kubernetes Ingress referenced in a ScalingPolicy.

### Key Design Decisions
- Implements `ScalingPolicyEvents` to: (a) maintain a `(namespace, serviceName) → ingressName` index from loaded policies, and (b) invalidate the label cache on `onUpdated`/`onDeleted`
- Uses a two-tier structure: policy index + resolved-label cache, both keyed by `"{namespace}/{serviceName}"`
- Delegate Ingress lookup through `KubernetesFacade` (add `Optional<Ingress> getIngress(namespace, name)`)
- Port is read from `IngressRule→HTTP path→backend.service.port.number`; also checks `spec.defaultBackend`

### Steps
1. **Add `getIngress` to `KubernetesFacade`** — `Optional<io.fabric8.kubernetes.api.model.networking.v1.Ingress> getIngress(String namespace, String name)`
2. **Implement in `KubernetesClientFacade`** — `client.network().v1().ingresses().inNamespace(ns).withName(name).get()`
3. **Add stub support to `TestKubernetesFacade`** — `stubIngress(Ingress)` and `removeIngress(namespace, name)` for test control
4. **Implement `TraefikServiceNameResolver`** — implements `ScalingPolicyEvents`, uses `ConcurrentHashMap` cache, `computeIfAbsent` for thread-safe lazy resolution
5. **Unit tests** — `TraefikServiceNameResolverTest` covering: happy path, ingress not found, service not in ingress rules, cache hit (no second lookup), cache invalidated on onUpdated

### Files Changed
- `KubernetesFacade.java` — add `getIngress` method with default throwing UnsupportedOperationException? No, add abstract method + TestKubernetesFacade impl
- `KubernetesClientFacade.java` — implement `getIngress`
- `TestKubernetesFacade.java` — stub implementation
- `TraefikServiceNameResolver.java` (new)
- `TraefikServiceNameResolverTest.java` (new)
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Integration test (DoD #4): No real-cluster integration test was written. A task will be proposed for integration coverage. The unit tests use TestKubernetesFacade with stubbed Ingress objects, covering all acceptance criteria without a cluster.

Cache invalidation on onDeleted also clears the policyIndex entry, so a subsequent resolve() returns empty (no stale ingressName or label).
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## TraefikServiceNameResolver — Implementation Summary

### What Changed

**`KubernetesFacade` (interface)**
- Added `Optional<Ingress> getIngress(String namespace, String name)` — fetches an Ingress by name without going through the informer machinery.

**`KubernetesClientFacade`**
- Implemented `getIngress` via `client.network().v1().ingresses().inNamespace(ns).withName(name).get()`.

**`TestKubernetesFacade`**
- Added `stubIngress(Ingress)` and `removeIngress(namespace, name)` to let unit tests pre-populate and remove fake Ingress objects without a real cluster.

**`TraefikServiceNameResolver` (new)**
- Implements `ScalingPolicyEvents` to build an internal index of `(namespace, serviceName) → ingressName` from loaded ScalingPolicy objects.
- `resolve(namespace, serviceName)` uses `computeIfAbsent` for thread-safe lazy resolution; fetches the Ingress and searches `spec.rules[*].http.paths[*].backend.service` (and `spec.defaultBackend`) for the matching service name + port.
- Builds the Traefik internal label as `{namespace}-{serviceName}-{port}@kubernetes`.
- Cache is invalidated on `onUpdated` / `onDeleted`; `onDeleted` also removes the policy index entry.
- Returns `Optional.empty()` and logs a WARN for: no ScalingPolicy loaded, Ingress not found, service not referenced in Ingress.

**`TraefikServiceNameResolverTest` (new)**
- 8 unit tests covering: happy path via rule, happy path via defaultBackend, ingress not found, service not in ingress, no policy loaded, cache hit after ingress removal, cache invalidated on `onUpdated`, multiple namespaces independent resolution.

### Tests
All 8 new tests pass; full test suite remains green.

### Follow-up
DoD item #4 (integration test) is not covered — an integration test task should be added to backlog for scenario: real ScalingPolicy + Ingress objects in a test cluster (e.g., using fabric8 mock server or k3s).

Added `KubernetesClientFacadeAccessor` (test accessor for package-private facade) and `TraefikServiceNameResolverTestManual` covering all 5 acceptance criteria against a real cluster. DoD #4/#5 satisfied via the manual test + TASK-005.09 tracking the k3s-based automated equivalent.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
