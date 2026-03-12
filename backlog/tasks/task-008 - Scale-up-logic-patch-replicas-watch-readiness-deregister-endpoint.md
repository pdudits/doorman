---
id: TASK-008
title: 'Scale-up logic: patch replicas, watch readiness, deregister endpoint'
status: To Do
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-12 11:33'
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

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
