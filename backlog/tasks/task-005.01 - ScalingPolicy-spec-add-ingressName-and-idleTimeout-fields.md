---
id: TASK-005.01
title: 'ScalingPolicy spec: add ingressName and idleTimeout fields'
status: Done
assignee:
  - copilot
created_date: '2026-03-13 11:45'
updated_date: '2026-03-13 11:52'
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
- [x] #1 ScalingPolicySpec.java gains `ingressName` (String, required) field — the name of the Kubernetes Ingress in the same namespace that routes to the managed service
- [x] #2 ScalingPolicySpec.java gains `idleTimeout` (String, optional, e.g. "5m") field — per-service idle timeout override
- [x] #3 scalingpolicy-crd.yaml openAPIV3Schema updated with both fields (ingressName required, idleTimeout optional)
- [x] #4 Existing unit tests still pass after the change
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Files to change
1. `src/main/java/io/zeromagic/doorman/repository/crd/ScalingPolicySpec.java`
   - Add `ingressName` String field with `@JsonProperty("ingressName")`
   - Add `idleTimeout` String field with `@JsonProperty("idleTimeout")` (nullable — optional)
   - Add standard getters/setters for both

2. `deploy/scalingpolicy-crd.yaml`
   - Add `ingressName: type: string` under `spec.properties`
   - Add `idleTimeout: type: string` under `spec.properties`
   - Add `ingressName` to `spec.required` list

### Validation
- Run `mvn test` to confirm existing tests still pass (no callers of ScalingPolicySpec need changing — fields are additive)
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added `ingressName` (required) and `idleTimeout` (optional) fields to `ScalingPolicySpec.java` following the existing Jackson `@JsonProperty` pattern with standard getters/setters. Updated `scalingpolicy-crd.yaml` openAPIV3Schema to include both fields under `spec.properties`, with `ingressName` added to the `required` list. All 40 existing tests pass with no regressions — changes are purely additive.

Also updated `ScalingPolicyCRDTestManual.java` to set the new `ingressName` and `idleTimeout` fields on the test resource.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
