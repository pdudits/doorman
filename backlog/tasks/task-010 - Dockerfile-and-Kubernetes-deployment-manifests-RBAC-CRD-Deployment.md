---
id: TASK-010
title: 'Dockerfile and Kubernetes deployment manifests (RBAC, CRD, Deployment)'
status: To Do
assignee: []
created_date: '2026-03-12 11:27'
labels:
  - deployment
  - docker
  - kubernetes
milestone: m-0
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Package Doorman as a container image and provide Kubernetes deployment manifests.

Required artifacts:
1. **Dockerfile**: Multi-stage build → minimal JRE 21 runtime image running the fat JAR
2. **deploy/crd.yaml**: ScalingPolicy CRD manifest
3. **deploy/doorman-deployment.yaml**: Doorman Deployment + Service + ServiceAccount with downward API for `MY_POD_IP`
4. **deploy/rbac.yaml**: ClusterRole with minimal permissions + ClusterRoleBinding

RBAC needs (at minimum):
- `deployments`: get, list, watch, patch (to scale)
- `endpointslices`: get, list, watch, create, patch, update, delete
- `scalingpolicies`: get, list, watch, update (for status patches)
- `pods`: get, list, watch (to detect readiness)
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 A `Dockerfile` builds a working image using a multi-stage build (build stage with Maven, runtime stage with JRE 21)
- [ ] #2 Image is based on a minimal JRE image (e.g., `eclipse-temurin:21-jre-alpine`)
- [ ] #3 The image runs the fat JAR as `java -jar /app/doorman.jar`
- [ ] #4 `MY_POD_IP` is documented as a required environment variable (injected by Kubernetes downward API)
- [ ] #5 A `deploy/` directory contains: `crd.yaml` (ScalingPolicy CRD), `doorman-deployment.yaml` (Doorman Deployment, ServiceAccount), `rbac.yaml` (ClusterRole + ClusterRoleBinding)
- [ ] #6 The Deployment manifest includes the `MY_POD_IP` downward API env injection
- [ ] #7 RBAC grants: get/list/watch/patch on Deployments, EndpointSlices, ScalingPolicies (custom resource); get/update on ScalingPolicy status subresource
- [ ] #8 A `README.md` documents how to build the image and apply the manifests
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
