---
id: TASK-013.08
title: 'Traefik v3 compatibility: run E2E suite against k3s v1.32+ with Traefik v3'
status: To Do
assignee: []
created_date: '2026-03-13 23:41'
labels:
  - e2e
  - system-test
  - traefik
milestone: m-0
dependencies:
  - TASK-013.05
parent_task_id: TASK-013
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add Traefik v3 compatibility: run the same E2E test suite against k3s v1.32+ which ships with Traefik v3 by default.

**Context:**
- k3s v1.31.x (current) ships with Traefik v2; k3s v1.32+ ships with Traefik v3
- Traefik v3 may use a different service label naming convention in metrics (`traefik_service_requests_total{service=...}`)
- In v2: `namespace-service-port@kubernetes`; in v3 the label construction may differ, especially for IngressRoute-based routing
- `TraefikServiceNameResolver` is tightly coupled to the v2 naming convention — it may need a v3 code path

**Scope:**
1. Create `TraefikV3K3sExtension extends TraefikK3sExtension` — same as v2 extension but uses k3s image `rancher/k3s:v1.32.x-k3s1` (pick latest stable patch)
2. Investigate actual service label format in Traefik v3 for standard Kubernetes Ingress:
   - Deploy nginx + Ingress in k3s v1.32, send a request, inspect metrics — find exact `service` label value
   - Compare to v2 format
3. If format differs: update `TraefikServiceNameResolver` to detect v3 format or add a configurable naming strategy
4. Refactor E2E test base: extract abstract `AbstractHappyPathE2EIT` shared by `HappyPathE2EIT` (v2) and `HappyPathV3E2EIT` (v3). Both subclasses supply the extension instance.
5. Run the full E2E happy-path scenario with v3

**Note on risk**: If Traefik v3 service label format is fundamentally dynamic (depends on IngressRoute naming), it may require Doorman to use a different idle-detection approach for v3 (e.g., scraping by router name rather than service name). This could result in a larger refactor — scope the investigation first before committing to a fix.

**Key files:**
- new `src/test/java/io/zeromagic/doorman/k3s/TraefikV3K3sExtension.java`
- updated `src/test/java/io/zeromagic/doorman/e2e/HappyPathE2EIT.java` — extract abstract base
- new `src/test/java/io/zeromagic/doorman/e2e/HappyPathV3E2EIT.java`
- possibly `src/main/java/io/zeromagic/doorman/traffic/TraefikServiceNameResolver.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TraefikV3K3sExtension starts k3s with Traefik v3 (rancher/k3s:v1.32.x)
- [ ] #2 Traefik v3 metrics endpoint returns expected service labels for standard Kubernetes Ingress
- [ ] #3 TraefikServiceNameResolver correctly resolves service labels under Traefik v3 (or is fixed)
- [ ] #4 All E2E happy-path scenarios from TASK-013.05 pass with Traefik v3
- [ ] #5 Differences in label format between v2 and v3 are documented
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
