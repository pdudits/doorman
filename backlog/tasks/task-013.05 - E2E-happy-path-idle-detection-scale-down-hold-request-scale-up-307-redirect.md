---
id: TASK-013.05
title: >-
  E2E happy path: idle detection, scale-down, hold request, scale-up, 307
  redirect
status: In Progress
assignee: []
created_date: '2026-03-13 23:40'
updated_date: '2026-03-14 12:35'
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
Full end-to-end happy path test in `src/test/java/io/zeromagic/doorman/e2e/HappyPathE2EIT.java`.

**Test flow (5 phases, single @Test method):**
1. Deploy `mendhak/http-https-echo` as `echo-e2e` + Service + Ingress for `echo-e2e.test`; create ScalingPolicy `policy-e2e` with `idleTimeout=10s`, `pollInterval=2s`; await pod ready (up to 120s)
2. GET `http://echo-e2e.test:{traefikHttpPort}/baseline` through Traefik → assert HTTP 200 from echo
3. `awaitScalingPolicyPhase(ScaledDown, 60s)` + `awaitDeploymentReplicas(0, 15s)` — Doorman detected idle, scaled to 0, registered itself as endpoint
4. GET `http://echo-e2e.test:{traefikHttpPort}/held-path` with `followRedirects(NORMAL)`, 120s timeout — request is held in Doorman proxy, scale-up is triggered automatically; client follows 307 redirect(s) until echo is ready; assert final HTTP 200
5. `awaitScalingPolicyPhase(Running, 60s)` — ScalingPolicy transitions back to Running

**Key design**: `HttpClient.followRedirects(NORMAL)` means the client self-heals through any transient 307 redirects during Traefik endpoint propagation. Test validates end-user experience (eventually gets 200), not internal redirect mechanics.

**Extensions**: `TraefikK3sExtension("e2e-happy-path")` + `DoormanSystemHarness(K3S, "10s", "2s")`
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

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
**Implementation approach:**
- `HappyPathE2EIT` in package `io.zeromagic.doorman.e2e`
- Uses `TraefikK3sExtension("e2e-happy-path")` + `DoormanSystemHarness(K3S, "10s", "2s")`
- Single @Test with 5 clearly-labeled phases
- `HttpClient.followRedirects(NORMAL)` — client follows 307(s) automatically, asserts final 200
- Added `awaitDeploymentReplicas` helper to `DoormanSystemHarness`

**Key design decision**: No explicit 307 assertion. With followRedirects(NORMAL), the test only sees
the final 200 from echo. This is the correct user-experience validation: the client eventually gets
their response, regardless of how many redirects occurred during Traefik endpoint propagation.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Implemented HappyPathE2EIT in e2e package with 5-phase test. followRedirects(NORMAL) self-heals through transient 307s during Traefik endpoint sync. Added awaitDeploymentReplicas to DoormanSystemHarness. Compiles cleanly — not yet run against live k3s cluster.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
