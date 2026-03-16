---
id: TASK-013
title: Full end-to-end system test suite with live Traefik in k3s
status: To Do
assignee: []
created_date: '2026-03-13 23:37'
updated_date: '2026-03-16 15:21'
labels:
  - system-test
  - e2e
  - traefik
  - k3s
milestone: m-0
dependencies: []
priority: high
ordinal: 5000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Run all Doorman components in-process against a real k3s Testcontainers cluster with Traefik as ingress controller. Validates the complete flow end-to-end: idle scraping → scale-down → endpoint registration → request holding → scale-up → 307 redirect → client follows redirect to real service.

**Key technical constraints:**
- k3s ships Traefik by default; ports need to be exposed from the container for tests to reach it
- Traefik Prometheus metrics are not enabled by default; requires HelmChartConfig in kube-system
- Doorman proxy runs in the test JVM (outside k3s); Traefik must be able to route to it via Docker host IP

**Also includes two prerequisite fixes/features before E2E tests can be written:**
- Fix state machine gap: ScaledDown → Running direct transition on manual scale-up
- New feature: reset idle timer when a new deployment rollout is detected
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 All E2E scenarios in subtasks pass reliably in CI
- [ ] #2 Traefik routes real HTTP traffic through to backend services in k3s
- [ ] #3 Doorman proxy (in-process) successfully receives traffic from Traefik via Docker host IP
- [ ] #4 Idle detection works with real Traefik Prometheus metrics
- [ ] #5 All happy-path and edge-case E2E scenarios implemented and green
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
