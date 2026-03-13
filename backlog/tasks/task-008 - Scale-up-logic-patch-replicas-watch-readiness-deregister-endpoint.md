---
id: TASK-008
title: 'Scale-up logic: patch replicas, watch readiness, deregister endpoint'
status: Done
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-13 23:18'
labels:
  - scaling
  - kubernetes
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the scale-up logic in the `scaling` package. This is the reverse of scale-down: when the proxy receives a request for a scaled-down service, it triggers a scale-up.

Flow:
1. Proxy signals the scaler to scale up
2. Scaler patches deployment `spec.replicas` to `targetReplicas`
3. Watches for `readyReplicas == targetReplicas` (via the Deployment watcher)
4. Removes Doorman's IP from **both** the EndpointSlice **and** the classic Endpoints object
5. Fires the readiness signal so proxy threads can unblock and send redirects

Both Endpoints and EndpointSlices must be cleaned up to ensure Traefik v2 and v3 stop routing to Doorman.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 When a request arrives for a `ScaledDown` service, `spec.replicas` is patched back to `targetReplicas` from the ScalingPolicy
- [ ] #2 ScalingPolicy status transitions to `ScalingUp` when the patch is issued
- [ ] #3 Doorman watches for the Deployment to report `readyReplicas == targetReplicas` (from the Deployment watcher in TASK-003)
- [ ] #4 When readiness is confirmed, Doorman removes its own IP from both the service's EndpointSlice AND the classic Endpoints object
- [ ] #5 After both deregistrations are confirmed, the readiness signal is fired, unblocking all waiting proxy threads
- [ ] #6 ScalingPolicy status transitions to `Running` after signal is fired
- [ ] #7 Scale-up is idempotent: if already in `ScalingUp` phase, a second incoming request just waits on the existing signal without issuing a second patch
- [ ] #8 If Doorman restarts while a service is `ScaledDown`, it re-registers its endpoint in both Endpoints and EndpointSlices and resumes normal operation
<!-- AC:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Implementation Decisions

**Deregister-before-signal ordering**: `onDeploymentChanged` calls `registrar.deregister()` *before* `confirmRunning()`. `confirmRunning()` completes the `CompletableFuture` and unblocks proxy virtual threads that then send 307 redirects. Deregistering first ensures doorman's EndpointSlice is deleted before clients retry. `deregister()` is idempotent — safe to call on every ready event.

**Propagation delay rationale**: Even after deregistration, Traefik needs time (tens of milliseconds) to reconcile its routing table. Without a delay, redirected clients would loop back to doorman. `ProxyServer` sleeps `config.propagationDelay()` (default `50ms`) after `future.get()` succeeds, before sending 307. This covers both the just-scaled-up case and requests for already-Running services (where Traefik may not have picked up the endpoint deletion yet). Virtual threads make the sleep free.

**`DurationParser` ms suffix — negative lookahead**: The original regex `(?:(\d+)m)?` would consume the `m` in `50ms`. Updated to `(?:(\d+)m(?!s))?` — the negative lookahead `(?!s)` prevents matching when `m` is followed by `s`, allowing `(?:(\d+)ms)?` to match the full `ms` suffix.

**ScaledDown → ScalingUp CAS**: `ScaledApplication.awaitReady()` uses `AtomicReference.compareAndSet`. The first caller transitions to `ScalingUp` and gets `scaleUpNeeded=true`; concurrent callers retry and get `scaleUpNeeded=false` on the next CAS attempt. `scaleUp()` and `patchStatus()` are only called when `scaleUpNeeded=true` — exactly once regardless of concurrency.

**`patchStatus` placement**: Called inside the `scaleUpNeeded` branch of `awaitReady()`, not in `onDeploymentChanged`. This ensures the status is patched to `ScalingUp` at the exact moment the scale-up is triggered, before any Kubernetes round-trip for the replica patch.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Completed scale-up logic across 5 focused changes:

1. **`KubernetesClientFacade.scaleUp()`** — implemented the stub: patches `spec.replicas = targetReplicas` using `.edit()`, mirrors `scaleDown()` exactly.

2. **`ScaledApplicationRegistry.awaitReady()`** — added `patchStatus(app)` call when `scaleUpNeeded` (i.e. ScaledDown → ScalingUp just happened), so ScalingPolicy status is updated to `ScalingUp` in Kubernetes at the moment scale-up is triggered.

3. **`ScaledApplicationRegistry.onDeploymentChanged()`** — added `registrar.deregister(snap.namespace(), snap.serviceName())` before `confirmRunning()` in the `ready >= 1` branch. This ensures doorman's EndpointSlice/Endpoints are actively deleted before the future completes and proxy threads send 307 redirects. Deregister is idempotent; called even on repeat ready events.

4. **`--propagation-delay` CLI arg** — new arg (default `50ms`) added to `CliArgs`, parsed via `DurationParser` (which was extended to handle `ms` suffix), added as 4th component of `DoormanConfig`. Updated `KubernetesClientFacadeAccessor` and `EndpointRegistrarIT` construction sites.

5. **`ProxyServer.handle()`** — after `future.get(scaleUpTimeout)` succeeds, `Thread.sleep(config.propagationDelay().toMillis())` before sending 307. Covers both Running-service requests (Traefik hasn't propagated endpoint deletion yet) and just-scaled-up cases. Virtual threads make the sleep cost-free.

New tests (137 total, all pass):
- `DurationParserTest`: `milliseconds()`, `secondsAndMilliseconds()`
- `ScaledApplicationRegistryTest`: `awaitReady_scaledDown_patchesStatusToScalingUp`, `awaitReady_scalingUp_doesNotPatchAgain`, `onDeploymentReady_callsDeregisterBeforeCompletingFuture`, `onDeploymentReady_deregisterCalledEvenIfAlreadyRunning`
- `ProxyServerTest`: `propagationDelay_is_observed_before_307`

IT test (k3s) for `scaleUp()` replica patch is left for a future task.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
