---
id: TASK-006
title: 'Scale-down logic: patch deployment to 0 and register Doorman endpoint'
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
- [ ] #1 When idle timeout is exceeded for a managed service currently in `Running` phase, the deployment's `spec.replicas` is patched to 0 via Fabric8
- [ ] #2 ScalingPolicy status is updated to `ScalingDown` before issuing the patch
- [ ] #3 Doorman adds its own IP to the service's **EndpointSlice** AND the classic **Endpoints** object after the deployment is patched to 0
- [ ] #4 EndpointSlice/Endpoints entries use the same port as the original service port and point to Doorman's proxy port
- [ ] #5 Doorman's IP is resolved from `--pod-ip` CLI arg first, falling back to `MY_POD_IP` environment variable; startup fails with a clear error if neither is set
- [ ] #6 Once the endpoint entries for Doorman are confirmed present in both resources, ScalingPolicy status transitions to `ScaledDown`
- [ ] #7 If the deployment already has 0 replicas when a ScalingPolicy is first created, Doorman immediately registers itself and moves to `ScaledDown`
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
