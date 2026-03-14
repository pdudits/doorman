---
id: TASK-013.05
title: >-
  E2E happy path: idle detection, scale-down, hold request, scale-up, 307
  redirect
status: To Do
assignee: []
created_date: '2026-03-13 23:40'
labels:
  - e2e
  - system-test
milestone: m-0
dependencies:
  - TASK-013.02
  - TASK-013.03
  - TASK-013.04
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Full end-to-end happy path test using `DoormanSystemHarness` and `TraefikK3sExtension`.

**Test flow:**
1. Deploy nginx (1 replica) + Service + Ingress for host `nginx.test`
2. Create ScalingPolicy with short idleTimeout (e.g. 10s), poll interval (2s)
3. Start DoormanSystemHarness
4. Verify `http://nginx.test:{traefikHttpPort}/` returns 200 from nginx
5. Wait for idle timeout to elapse (no requests sent) — `awaitScalingPolicyPhase(ScaledDown)` + `awaitReplicas(0)`
6. Verify Doorman's endpoint is registered (EndpointSlice has Doorman's IP)
7. Send request to `http://nginx.test:{traefikHttpPort}/` — Traefik routes to Doorman proxy
8. In parallel: wait for Doorman to call scaleUp, then trigger `onDeploymentChanged` with ready=1 (or wait for real nginx pod)
9. Assert HTTP 307 response with `Location: http://nginx.test:{traefikHttpPort}/`
10. Follow redirect — assert 200 from nginx
11. Assert ScalingPolicy status = Running

**Note on timing**: Use real nginx pods if k3s can pull images fast enough, or mock the deployment readiness in the harness by directly firing `onDeploymentChanged`. Real pods are strongly preferred to catch actual Traefik routing issues.

**Key files:**
- new `src/test/java/io/zeromagic/doorman/e2e/HappyPathE2EIT.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Traffic flows through Traefik to nginx initially (HTTP 200 from nginx)
- [ ] #2 After idle timeout: ScalingPolicy status = ScaledDown, deployment spec.replicas = 0
- [ ] #3 Client request to Doorman proxy (via Traefik) is held while service is down
- [ ] #4 After scale-up: client receives HTTP 307 with correct Location header
- [ ] #5 Client follows redirect and gets HTTP 200 from nginx
- [ ] #6 ScalingPolicy status = Running after redirect
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
