---
id: TASK-017
title: Blackbox end-to-end test with Doorman deployed in-cluster
status: Done
assignee: []
created_date: '2026-03-17 09:25'
updated_date: '2026-03-17 11:12'
labels:
  - testing
  - e2e
  - docker
dependencies: []
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
A fully blackbox end-to-end test where Doorman runs as a real pod inside k3s (deployed from deploy/ manifests), and the test only speaks to Traefik. Repeats the happy-path scenario of HappyPathE2EIT.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Doorman image is imported into k3s containerd before tests run
- [x] #2 deploy/ manifests (namespace, SA, RBAC) are applied to the cluster
- [x] #3 Doorman pod runs in doorman-system namespace with imagePullPolicy=Never and traefik discovered by label selector
- [x] #4 Doorman pod logs are captured to SLF4J during test
- [x] #5 Full happy-path scenario passes: baseline 200 → scale-down → held request 307→200 → ScalingPolicy Running
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation

### pom.xml
- Added namespace.yaml, serviceaccount.yaml, rbac.yaml to testResources
- Added `<excludedGroups>blackbox</excludedGroups>` to default failsafe config
- Added `blackbox` profile: docker:save (pre-integration-test → target/doorman-test.tar) + failsafe with `<groups>blackbox</groups>` + system property `doorman.image.tar`

### Refactoring
- Moved `deployEchoApp`, `createIngress`, `createScalingPolicy`, `awaitPodReady`, `awaitScalingPolicyPhase`, `awaitDeploymentReplicas` from DoormanSystemHarness to K3sClusterExtension
- DoormanSystemHarness now delegates to `ext.*`
- Added `getK3sContainer()` protected accessor to K3sClusterExtension

### New classes
- `DeployedDoormanK3sExtension` — imports image, applies manifests, creates Deployment, watches logs
- `DeployedHappyPathE2EIT` — @Tag("blackbox") 5-phase happy path test

### Run with
mvn verify -P blackbox
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
All 5 acceptance criteria met. The blackbox happy-path test passes in ~110s:

- Phase 1: echo app, ingress, ScalingPolicy deployed
- Phase 2: baseline GET returns 200 through Traefik
- Phase 3: Doorman detects idle (10s), scales deployment to 0, registers as endpoint
- Phase 4: held request triggers ScaledDown→ScalingUp→Running; 307 redirect followed to echo returning 200
- Phase 5: ScalingPolicy phase returns to Running

Key fixes discovered during implementation:
- Main.java had debug stub calling scope.get(KubernetesClient.class) which doesn't exist as a bean — replaced with Thread.join() to keep JVM alive
- KubernetesClientFacade.inform() used bare client.resources(type) which scopes to client namespace (doorman-system) — fixed with inAnyNamespace()
- RBAC was missing: ingresses, services, scalingpolicies/status
- TraefikK3sExtension now binds port 80:80 (fixed) so 307 redirects to http://*.test/ resolve correctly via TestDotResolverProvider
- doorman.version system property passed to failsafe so DeployedDoormanK3sExtension can build the correct classpath
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
