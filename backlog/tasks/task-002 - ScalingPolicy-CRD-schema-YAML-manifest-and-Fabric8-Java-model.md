---
id: TASK-002
title: 'ScalingPolicy CRD: schema, YAML manifest, and Fabric8 Java model'
status: To Do
assignee: []
created_date: '2026-03-12 11:25'
updated_date: '2026-03-12 11:33'
labels:
  - crd
  - kubernetes
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Design and implement the ScalingPolicy Custom Resource Definition. This CRD is the primary user-facing configuration object that links together a Traefik-tracked Service with a Deployment that Doorman should manage.

The CRD serves as both configuration and status store:
- **Spec**: Links routes (host+path prefix) → deployment, configures idle timeout, target replicas, and the Traefik service name used in metrics
- **Status**: Reflects current lifecycle phase and last transition time (updated by Doorman)

A single ScalingPolicy can declare multiple `routes` (host + path prefix combinations) that all funnel traffic to the same managed deployment. This allows e.g. `example.com/api` and `api.example.com/` to both wake the same backend.

Java model classes need to be created for use with Fabric8's typed CRD API (`CustomResource<Spec, Status>` subclass).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 CRD YAML manifest is valid and can be applied to a cluster with `kubectl apply -f`
- [ ] #2 CRD group: `doorman.zeromagic.io`, kind: `ScalingPolicy`
- [ ] #3 Spec fields: `serviceName` (string — the K8s Service name, for EndpointSlice/Endpoints manipulation), `deploymentName` (string), `namespace` (string), `idleTimeout` (duration string e.g. `10m`), `targetReplicas` (integer, defaults to 1), `traefikServiceName` (string — the name Traefik uses in its metrics), `routes` (list of objects with `host` and `pathPrefix` fields — the ingress routes whose traffic Doorman should intercept)
- [ ] #4 Status fields: `phase` (enum: Running/ScalingDown/ScaledDown/ScalingUp), `lastTransitionTime` (timestamp), `message` (string, optional)
- [ ] #5 Fabric8 Java model class (`ScalingPolicy`, `ScalingPolicySpec`, `ScalingPolicyStatus`, `ScalingPolicyRoute`) generated or hand-written and registered
- [ ] #6 A sample `ScalingPolicy` YAML shows a deployment reachable via two different host/path combinations in its `routes` list
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
