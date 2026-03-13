---
id: TASK-007.02
title: 'IngressRouteIndex: resolve Host+path prefix to (namespace, serviceName)'
status: To Do
assignee: []
created_date: '2026-03-13 20:26'
labels:
  - proxy
  - kubernetes
milestone: m-0
dependencies:
  - TASK-007.01
parent_task_id: TASK-007
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
New `@Singleton` class implementing `ScalingPolicyEvents` that builds a routing table from Ingress host/path rules.

**Pattern to follow:** `TraefikServiceNameResolver` — same structure (implements `ScalingPolicyEvents`, uses `facade.getIngress()`, indexes by policy key).

**Scope:**
1. New record `RouteTarget(String namespace, String serviceName)` in `proxy/`
2. New class `IngressRouteIndex` in `proxy/`:
   - `@Singleton`, `@Inject` constructor taking `KubernetesFacade`
   - Implements `ScalingPolicyEvents`
   - On `onAdded`/`onUpdated`: call `facade.getIngress(ns, ingressName)`, walk all `spec.rules[].http.paths[]` and `spec.defaultBackend` where `backend.service.name == spec.serviceName`, index `(host, pathPrefix) → RouteTarget(ns, svc)`. Also handle empty/null `ingressName` gracefully.
   - On `onDeleted`: remove all entries for that policy's (ns, svc)
3. `resolve(String host, String path) → Optional<RouteTarget>`:
   - Strip port from host if present (`host:port`)
   - Find all entries matching the host
   - Among matching entries, pick the one with the longest pathPrefix that is a prefix of `path`
   - Return empty if none match

**Fabric8 Ingress model** (already used in codebase):
- `ingress.getSpec().getRules()` → `List<IngressRule>`
- `rule.getHost()` → hostname string
- `rule.getHttp().getPaths()` → `List<HTTPIngressPath>`
- `path.getPath()` → path string
- `path.getBackend().getService().getName()` → backend service name

**Key files:**
- new `src/main/java/io/zeromagic/doorman/proxy/RouteTarget.java`
- new `src/main/java/io/zeromagic/doorman/proxy/IngressRouteIndex.java`
- new `src/test/java/io/zeromagic/doorman/proxy/IngressRouteIndexTest.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 onAdded/onUpdated fetches the Ingress via facade.getIngress() and indexes all host+pathPrefix rules where backend.service.name == spec.serviceName
- [ ] #2 onDeleted removes all route entries for that policy
- [ ] #3 resolve(host, path) returns the correct RouteTarget using longest-prefix match on path
- [ ] #4 resolve returns empty for unknown host or path
- [ ] #5 Unit tests cover: basic resolution, longest-prefix wins, deleted policy entries are removed, missing Ingress logs a warning and adds no entries
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
