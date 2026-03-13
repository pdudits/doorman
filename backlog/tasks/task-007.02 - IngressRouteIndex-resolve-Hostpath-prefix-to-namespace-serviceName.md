---
id: TASK-007.02
title: 'IngressRouteIndex: resolve Host+path prefix to (namespace, serviceName)'
status: Done
assignee: []
created_date: '2026-03-13 20:26'
updated_date: '2026-03-13 23:17'
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
- [x] #1 onAdded/onUpdated fetches the Ingress via facade.getIngress() and indexes all host+pathPrefix rules where backend.service.name == spec.serviceName
- [x] #2 onDeleted removes all route entries for that policy
- [x] #3 resolve(host, path) returns the correct RouteTarget using longest-prefix match on path
- [x] #4 resolve returns empty for unknown host or path
- [x] #5 Unit tests cover: basic resolution, longest-prefix wins, deleted policy entries are removed, missing Ingress logs a warning and adds no entries
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Decisions

**Two-level index structure**: `ConcurrentHashMap<host, HostRoutes>` as primary lookup; a separate `ConcurrentHashMap<policyKey, List<RouteKey>>` tracks which entries each policy owns — needed for clean `onDeleted` removal without a full scan.

**`HostRoutes` internal class**: Encapsulates a `ReentrantReadWriteLock`-guarded `List<RouteEntry>` kept sorted descending by `pathPrefix.length()`. `resolve(path)` iterates and returns the first `startsWith` match — first match equals longest prefix.

**Port stripping in `IngressRouteIndex.resolve()`**: Ingress rules store bare hostnames; HTTP requests send `Host: example.com:8080`. Port is stripped from the host argument before map lookup. The same stripping is also needed in `ProxyServer` before calling `routeIndex.resolve()`.

**Default backends skipped**: `spec.defaultBackend` (no host) is not indexed — would require host=`*` semantics and add complexity not needed for the current use case.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Created 3 new files in proxy/ package:
- `RouteTarget.java` — record(namespace, serviceName)
- `HostRoutes.java` — package-private class owning a List<RouteEntry> sorted descending by pathPrefix.length(), protected by ReentrantReadWriteLock. Methods: add(), remove(), resolve(path), isEmpty().
- `IngressRouteIndex.java` — @Singleton implementing ScalingPolicyEvents. ConcurrentHashMap<host, HostRoutes> as primary index; ConcurrentHashMap<policyKey, List<RouteKey>> for clean deletion. onAdded/onUpdated fetches Ingress via facade, walks rules/paths for matching service name, inserts sorted. Default backends (no host) skipped. onDeleted removes tracked entries. resolve() strips port from host, delegates to HostRoutes.resolve() for first-match = longest-prefix.
- 10 unit tests covering: basic match, longest-prefix wins, unknown host, unmatched path, onDeleted, onUpdated replaces, host:port stripping, blank ingressName, ingress not found, service not in ingress. All green.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
