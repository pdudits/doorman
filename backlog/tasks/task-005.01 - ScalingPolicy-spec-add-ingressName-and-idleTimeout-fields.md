---
id: TASK-005.01
title: 'ScalingPolicy spec: add ingressName and idleTimeout fields'
status: To Do
assignee: []
created_date: '2026-03-13 11:45'
labels:
  - crd
  - traffic
milestone: m-0
dependencies: []
references:
  - src/main/java/io/zeromagic/doorman/crd/
  - deploy/scalingpolicy-crd.yaml
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Extend `ScalingPolicy` CRD spec to carry the two new fields needed by the traffic subsystem. These fields are consumed by Task-005 subtasks for Traefik service name resolution and idle timeout configuration.

Context: ScalingPolicy is the custom resource at `src/main/java/io/zeromagic/doorman/crd/`. The CRD YAML lives at `deploy/scalingpolicy-crd.yaml`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 ScalingPolicySpec.java gains `ingressName` (String, required) field — the name of the Kubernetes Ingress in the same namespace that routes to the managed service
- [ ] #2 ScalingPolicySpec.java gains `idleTimeout` (String, optional, e.g. "5m") field — per-service idle timeout override
- [ ] #3 scalingpolicy-crd.yaml openAPIV3Schema updated with both fields (ingressName required, idleTimeout optional)
- [ ] #4 Existing unit tests still pass after the change
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
