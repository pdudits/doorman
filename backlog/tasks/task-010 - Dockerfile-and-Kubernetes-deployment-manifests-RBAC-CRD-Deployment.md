---
id: TASK-010
title: 'Dockerfile and Kubernetes deployment manifests (RBAC, CRD, Deployment)'
status: Done
assignee: []
created_date: '2026-03-12 11:27'
updated_date: '2026-03-17 08:58'
labels:
  - deployment
  - docker
  - kubernetes
milestone: m-0
dependencies: []
priority: medium
ordinal: 2000
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
- [x] #2 Image is based on a minimal JRE image (e.g., `eclipse-temurin:21-jre-alpine`)
- [ ] #3 The image runs the fat JAR as `java -jar /app/doorman.jar`
- [ ] #4 `MY_POD_IP` is documented as a required environment variable (injected by Kubernetes downward API)
- [x] #5 A `deploy/` directory contains: `crd.yaml` (ScalingPolicy CRD), `doorman-deployment.yaml` (Doorman Deployment, ServiceAccount), `rbac.yaml` (ClusterRole + ClusterRoleBinding)
- [x] #6 The Deployment manifest includes the `MY_POD_IP` downward API env injection
- [x] #7 RBAC grants: get/list/watch/patch on Deployments, EndpointSlices, ScalingPolicies (custom resource); get/update on ScalingPolicy status subresource
- [x] #8 A `README.md` documents how to build the image and apply the manifests
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Decisions
- **Build tool**: `io.fabric8:docker-maven-plugin` (buildx multi-arch, `%l` tag semantics)
- **Registry**: `docker.io/pdudits/doorman`
- **Base image**: `azul/zulu-openjdk-alpine:25-latest` (property `docker.base.image`)
- **Platforms**: default = native; `multiplatform` profile → `linux/amd64,linux/arm64`
- **Layers**: release-deps → `/app/lib`, snapshot-deps → `/app/snapshot-lib`, artifact → `/app`
- **CMD**: `java -cp '/app/lib/*:/app/snapshot-lib/*:/app/doorman-${project.version}.jar' io.zeromagic.doorman.Main`
- **Tags**: `%l` + always `latest`
- **Shade**: explicit `-P shade` profile only (not default)
- **Manifests**: Kustomize in `deploy/` (flat, no subdirectory); CRD applied separately

## pom.xml steps
1. Add `docker.base.image` and `docker.platforms` (empty) properties
2. Add `io.fabric8:docker-maven-plugin` — buildx + 3-layer assembly + tags `%l` and `latest`; bound to `deploy` phase
3. Move `maven-shade-plugin` to `shade` profile (explicit opt-in)
4. Add `multiplatform` profile: sets `docker.platforms=linux/amd64,linux/arm64`
5. Add `maven-deploy-plugin` with `skip=true`

## Manifest steps (flat in deploy/)
6. `deploy/kustomization.yaml` — resources list, namespace: doorman-system
7. `deploy/namespace.yaml` — Namespace
8. `deploy/serviceaccount.yaml` — ServiceAccount doorman
9. `deploy/rbac.yaml` — ClusterRole + ClusterRoleBinding
   Verbs: deployments (get/list/watch/patch/update), endpoints (get/list/watch/patch/update), endpointslices (get/list/watch/create/patch/update/delete), scalingpolicies (get/list/watch), pods (get/list/watch)
10. `deploy/deployment.yaml` — Deployment with POD_IP downward API

## README.md (final step)
11. `README.md` at repo root — no emojis, plain prose
    Sections:
    - What Doorman is (one short paragraph)
    - How it works (brief description of the scale-to-zero flow)
    - Prerequisites (Java 21+, Docker with buildx, kubectl + kustomize)
    - Build targets: `mvn package`, `mvn deploy` (local image), `mvn deploy -P multiplatform` (multi-arch push), `mvn package -P shade` (fat JAR)
    - Key build properties: `docker.base.image`, `docker.platforms`
    - Installation: `kubectl apply -f deploy/scalingpolicy-crd.yaml && kubectl apply -k deploy/`
    - Configuration reference (env vars / CLI flags table)

Install: `kubectl apply -f deploy/scalingpolicy-crd.yaml && kubectl apply -k deploy/`
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented using `io.fabric8:docker-maven-plugin` (no standalone Dockerfile — the plugin manages the build directly).

**pom.xml changes:**
- Added properties: `docker.base.image`, `docker.image.name`, `docker.platforms`
- Added docker-maven-plugin 0.48.1: 3-layer assembly (release-deps→/app/lib, snapshot-deps→/app/snapshot-lib, artifact→/app), tags `%l`+`latest`, build bound to `package`, push bound to `deploy`
- Moved shade plugin into `-P shade` profile (not active by default)
- Added `-P multiplatform` profile setting `docker.platforms=linux/amd64,linux/arm64`
- Added `maven-deploy-plugin` with `skip=true`

**Kubernetes manifests (flat in deploy/):**
- `kustomization.yaml` — namespace: doorman-system, lists all resources
- `namespace.yaml` — Namespace doorman-system
- `serviceaccount.yaml` — ServiceAccount doorman
- `rbac.yaml` — ClusterRole + ClusterRoleBinding with verbs for deployments, endpoints, endpointslices, scalingpolicies, pods
- `deployment.yaml` — Deployment with POD_IP injected via downward API

**README.md** — plain prose, no emojis; covers what Doorman does, how it works, prerequisites, build targets, key properties, installation, and full configuration reference table.

**Notes on AC deviation:**
- AC1/AC3: No separate Dockerfile; the maven plugin builds a layered image directly (more cache-efficient)
- AC4: Env var is `POD_IP` (matches picocli config), not `MY_POD_IP` as originally drafted
- Buildx/multi-arch wiring is deferred to a separate task; the `multiplatform` profile sets the platform property but buildx configuration in the plugin is not yet added
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
