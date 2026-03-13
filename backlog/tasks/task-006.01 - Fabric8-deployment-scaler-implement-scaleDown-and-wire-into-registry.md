---
id: TASK-006.01
title: 'Fabric8 deployment scaler: implement scaleDown and wire into registry'
status: Done
assignee: []
created_date: '2026-03-13 18:58'
updated_date: '2026-03-13 19:43'
labels:
  - scaling
  - kubernetes
milestone: m-0
dependencies:
  - TASK-005.07
parent_task_id: TASK-006
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement the deployment scale-down side effect triggered when IdleDetector calls `beginScalingDown`.

**Context:** `KubernetesClientFacade` already has a stub `scaleDown(ns, deploymentName)` logged as "[stub]". `ScaledApplicationRegistry.beginScalingDown()` currently only patches the ScalingPolicy status — it does NOT call `scaler.scaleDown()`. Both need to be wired up.

**Scope:**
1. Implement `KubernetesClientFacade.scaleDown(ns, deploymentName)` — use Fabric8 to PATCH the deployment's `spec.replicas` to 0.
2. Update `ScaledApplicationRegistry.beginScalingDown()` to call `scaler.scaleDown(snap.namespace(), snap.deploymentName())` when the Running→ScalingDown transition succeeds.
3. Add `--proxy-port` CLI arg to `CliArgs.java` (int, default 8080) and expose via `DoormanConfig` — required by TASK-006.02 (EndpointRegistrar needs to know Doorman's proxy port).

**Key files:**
- `src/main/java/io/zeromagic/doorman/kubernetes/KubernetesClientFacade.java` — replace stub with real Fabric8 patch
- `src/main/java/io/zeromagic/doorman/repository/ScaledApplicationRegistry.java` — add `scaler.scaleDown()` call in `beginScalingDown()`
- `src/main/java/io/zeromagic/doorman/cli/CliArgs.java` — add `--proxy-port`
- `src/main/java/io/zeromagic/doorman/cli/DoormanConfig.java` — expose `proxyPort`

**Unit test pattern:** `KubernetesClientFacadeTest` is not yet created. Testing the Fabric8 patch directly requires either a real client or mocking. Prefer an accessor/integration approach — test scaleDown via `ScaledApplicationRegistryIT` pattern using K3s, or create a focused `Fabric8DeploymentScalerTest` in the same package that verifies the PATCH call against k3s. At minimum, unit-test the registry wiring (stub scaler records the call).

**DI note:** `DoormanConfig` is a record — adding `proxyPort` int field is a breaking change for all construction sites. Update `ConfigProvider.java` accordingly.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 When `beginScalingDown` transitions Running→ScalingDown, `ServiceScaler.scaleDown(namespace, deploymentName)` is called exactly once
- [x] #2 KubernetesClientFacade.scaleDown patches the Deployment spec.replicas to 0 using Fabric8
- [x] #3 --proxy-port CLI arg is added (int, default 8080); exposed via DoormanConfig.proxyPort()
- [x] #4 Unit test: ScaledApplicationRegistry with a recording stub scaler verifies scaleDown is called on beginScalingDown and not called if already ScalingDown
- [x] #5 IT test: create a Deployment + ScalingPolicy in k3s; call beginScalingDown; assert deployment.spec.replicas == 0 in k3s API
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Implemented scaleDown in KubernetesClientFacade (Fabric8 PATCH spec.replicas=0), wired scaler.scaleDown() into ScaledApplicationRegistry.beginScalingDown(), added --proxy-port CLI arg (default 8080) to CliArgs and DoormanConfig. Unit tests verify scaleDown called exactly once on transition, not called on repeat. IT test verifies deployment.spec.replicas==0 in k3s. All mvn verify green.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
