---
id: TASK-006
title: 'Scale-down logic: patch deployment to 0 and register Doorman endpoint'
status: Done
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-13 20:15'
labels:
  - scaling
  - kubernetes
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the scale-down logic in the `scaling` package. When the traffic monitor signals that a service is idle, this component:

1. Patches the Deployment `spec.replicas` to 0 (Fabric8 patch)
2. Adds Doorman's own IP to the service's **EndpointSlice** (new API) AND the classic **Endpoints** object so traffic from both Traefik v2 and v3 is intercepted
3. The real pods will be terminated by Kubernetes naturally; Doorman doesn't need to wait for them

Doorman's own IP is resolved with this priority:
1. `--pod-ip` CLI argument (useful for local/tunnel development)
2. `MY_POD_IP` environment variable (standard Kubernetes Downward API injection)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 When idle timeout is exceeded for a managed service currently in `Running` phase, the deployment's `spec.replicas` is patched to 0 via Fabric8
- [x] #2 ScalingPolicy status is updated to `ScalingDown` before issuing the patch
- [x] #3 Doorman adds its own IP to the service's **EndpointSlice** AND the classic **Endpoints** object after the deployment is patched to 0
- [x] #4 EndpointSlice/Endpoints entries use the same port as the original service port and point to Doorman's proxy port
- [x] #5 Doorman's IP is resolved from `--pod-ip` CLI arg first, falling back to `MY_POD_IP` environment variable; startup fails with a clear error if neither is set
- [x] #6 Once the endpoint entries for Doorman are confirmed present in both resources, ScalingPolicy status transitions to `ScaledDown`
- [x] #7 If the deployment already has 0 replicas when a ScalingPolicy is first created, Doorman immediately registers itself and moves to `ScaledDown`
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All scale-down logic implemented across two subtasks:

**TASK-006.01** — KubernetesClientFacade.scaleDown() patches Deployment spec.replicas=0 via Fabric8. ScaledApplicationRegistry.beginScalingDown() wired to call scaler.scaleDown(). --proxy-port CLI arg added (default 8080), exposed via DoormanConfig. Unit + IT tests green.

**TASK-006.02** — EndpointRegistrar register()/deregister() implemented for both EndpointSlice (doorman-<svc> slice) and classic Endpoints. Idempotent. Fight-back loop wired via EndpointsInformer/EndpointSliceInformer. ScaledApplicationRegistry calls register() after every confirmScaledDown() and on startup when initial state is ScaledDown. Startup fails with clear error if podIp unresolvable. Unit + IT tests green (register idempotency, deregister, registry integration).
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
