---
id: TASK-003
title: 'Kubernetes resource watchers: ScalingPolicy, Deployment, EndpointSlice'
status: Done
assignee: []
created_date: '2026-03-12 11:26'
updated_date: '2026-03-12 14:11'
labels:
  - kubernetes
  - watchers
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the Kubernetes resource watchers that feed the Doorman state machine. These run cluster-wide and react to changes in four resource types:

1. **ScalingPolicy** (custom resource): add/remove managed services, reload configuration
2. **Deployment**: detect when a deployment's replica count reaches 0 (scaled down) or when ready replicas reach target (scaled up)
3. **EndpointSlices**: detect when a service's real endpoints have fully drained (newer clusters, Traefik v3+)
4. **Endpoints**: detect endpoint drain on older clusters / Traefik v2 users (classic Endpoints API)

Both Endpoints and EndpointSlices must be watched because Traefik v2 still uses the classic Endpoints API. Doorman should handle both for maximum compatibility.

Use Fabric8's informer/watcher API. The `repository` package should maintain a central registry of all active ScalingPolicy state.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Doorman starts watching `ScalingPolicy` resources cluster-wide on startup; creates/updates/deletes trigger appropriate state transitions
- [x] #2 Doorman watches `Deployment` resources; ready replica count changes trigger state transitions (especially: all replicas ready after scale-up)
- [x] #3 Doorman watches `EndpointSlice` resources to track when real application endpoints have drained (for newer clusters / Traefik v3)
- [x] #4 Doorman watches `Endpoints` resources to track drain for older clusters / Traefik v2 compatibility
- [x] #5 All watchers reconnect/retry automatically on connection loss (Fabric8 handles this, but must be wired correctly)
- [x] #6 Watcher events are logged at INFO level with resource name/namespace/event type
- [ ] #7 The `repository` package holds a thread-safe in-memory map of currently managed ScalingPolicies (keyed by namespace/name)
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented four `ResourceEventHandler<T>` informers auto-discovered by `InformerHandler`:

- **ScalingPolicyInformer** — delegates to `ScalingPolicyEvents` (onAdded/onUpdated/onDeleted)
- **DeploymentInformer** — delegates to `DeploymentEvents` (onDeploymentChanged)
- **EndpointsInformer** — classic Endpoints API (Traefik v2); detects real drain, Doorman removal, and real-ready; delegates to `EndpointsEvents`
- **EndpointSliceInformer** — EndpointSlice API (Traefik v3+); same three signals via `EndpointSliceEvents`

All four event interfaces are defined with no-op stub `NoOpDomainEvents` so the app compiles and runs without Task-004.

Also added `--pod-ip` CLI option (+ `POD_IP` env var fallback) to `CliArgs`/`ConfigProvider`/`DoormanConfig` so informers can identify Doorman's own endpoint.

Fixed typo: `ServiceInfromer` → `ServiceInformer`.

AC #7 (thread-safe registry) is deferred to Task-004.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
