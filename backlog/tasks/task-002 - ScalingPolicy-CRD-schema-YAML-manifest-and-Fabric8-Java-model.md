---
id: TASK-002
title: 'ScalingPolicy CRD: schema, YAML manifest, and Fabric8 Java model'
status: Done
assignee: []
created_date: '2026-03-12 11:25'
updated_date: '2026-03-12 13:20'
labels:
  - crd
  - kubernetes
milestone: m-0
dependencies: []
priority: high
---
## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Design and implement the ScalingPolicy Custom Resource Definition.

The CRD is namespace-scoped and links an existing Kubernetes Service to a Deployment Doorman should manage.

- **Spec** only includes `serviceName` and `deploymentName`.
- **Status** tracks the current phase (Running, ScalingDown, ScaledDown, ScalingUp, Stopped), `lastTransitionTime`, optional `message`, and the `targetReplicas` value copied from the Deployment.

Java model classes should be provided so the Fabric8 controllers can read/write strongly-typed CRD instances.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CRD YAML manifest is valid and can be applied to a cluster with `kubectl apply -f`
- [ ] #2 CRD group: `doorman.zeromagic.io`, kind: `ScalingPolicy`, scope: Namespaced
- [ ] #3 Spec fields: `serviceName` (string) and `deploymentName` (string) only
- [ ] #4 Status fields include `phase` (enum: Running/ScalingDown/ScaledDown/ScalingUp/Stopped), `lastTransitionTime` (timestamp), `message` (string, optional), and `targetReplicas` (integer)
- [ ] #5 Fabric8 Java model class (`ScalingPolicy`, `ScalingPolicySpec`, `ScalingPolicyStatus`, `ScalingPolicyPhase`) generated or hand-written and used by the operator
- [ ] #6 A sample `ScalingPolicy` YAML demonstrates both a Running status and a Stopped status (replicas 0 because user scaled down)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan for TASK-002
1. Define the `ScalingPolicy` CRD (namespace-scoped) with a spec that only includes `serviceName` and `deploymentName` and a status that tracks `phase` (enum: Running, ScalingDown, ScaledDown, ScalingUp, Stopped), `lastTransitionTime`, `message`, and `targetReplicas`.
2. Write the CRD YAML manifest with OpenAPI schema validation for the spec/status and set the `names`/`scope` appropriately.
3. Implement the Fabric8 models (`ScalingPolicy`, `ScalingPolicySpec`, `ScalingPolicyStatus`, `ScalingPolicyPhase`) so the watcher/state machine code can rely on typed objects.
4. Provide a sample `ScalingPolicy` YAML showing both a normal `Running` state and a `Stopped` state that represents a user-scaled-to-zero deployment.
5. Wire the generated model into the Fabric8 module (e.g., add the annotation processor or manually create the classes) so Doorman can watch the custom resource.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Added `deploy/scalingpolicy-crd.yaml`, sample Running/Stopped manifests, and Fabric8 POJOs for `ScalingPolicy`, `ScalingPolicySpec`, `ScalingPolicyStatus`, and `ScalingPolicyPhase`. `mvn clean package` passes after these additions. Next up: wiring watchers/state machine around this CRD.
<!-- SECTION:NOTES:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
