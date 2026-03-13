---
id: TASK-006.02
title: >-
  Fabric8 endpoint registrar: register/deregister Doorman IP in EndpointSlice
  and Endpoints
status: To Do
assignee: []
created_date: '2026-03-13 18:59'
updated_date: '2026-03-13 19:02'
labels:
  - scaling
  - kubernetes
milestone: m-0
dependencies:
  - TASK-006.01
parent_task_id: TASK-006
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement EndpointRegistrar: register/deregister Doorman's own IP in both EndpointSlice and classic Endpoints when a deployment is scaled to zero.

KubernetesClientFacade has stubs. ScaledApplicationRegistry already calls registrar.register() in fight-back handlers but not after the initial confirmScaledDown transition. --proxy-port arg and DoormanConfig.proxyPort() are added by TASK-006.01.

**EndpointSlice:** find slice(s) labeled kubernetes.io/service-name=<svc>, reuse port definitions (same name/protocol) but replace port with proxyPort, add endpoint addresses=[doormanIp] conditions.ready=true. Create new slice if none exists. Idempotent.

**Classic Endpoints:** PATCH the Endpoints object named serviceName, add subset with doormanIp and proxyPort. Idempotent.

**Fight-back loop (keeping endpoints registered while scaled down):**
Kubernetes will continuously remove Doorman's IP from endpoints because Doorman's pod does not carry the service selector labels. The fight-back loop is already wired in the registry:
- `EndpointsInformer.evaluate()` → if doormanPresent==false → `onDoormanEndpointRemoved(ns, svc)` → `registrar.register(ns, svc)` (when ScaledDown or ScalingUp)
- `EndpointSliceInformer.evaluate()` → if doormanPresent==false → `onDoormanSliceRemoved(ns, svc)` → `registrar.register(ns, svc)` (when ScaledDown or ScalingUp)
The real `register()` implementation must be idempotent and performant enough to handle being called repeatedly. The fight-back mechanism ensures Doorman stays registered for the duration of the ScaledDown/ScalingUp states.

**Registry wiring:** after each confirmScaledDown() in onDeploymentChanged, onRealEndpointsDrained, onRealSlicesDrained, call registrar.register(ns, svc). In onAdded, if initial state is ScaledDown, call registrar.register() for AC#7 of parent task.

**Startup validation:** if podIp is null after checking --pod-ip and POD_IP env, fail with clear error in ConfigProvider.java.

**DI:** KubernetesClientFacade needs DoormanConfig injected for podIp and proxyPort. Add to constructor; annotate @jakarta.inject.Inject if multiple constructors.

Key files: KubernetesClientFacade.java, ScaledApplicationRegistry.java, ConfigProvider.java
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 register() adds Doorman IP to EndpointSlice labeled kubernetes.io/service-name=<svc> with proxy port; idempotent
- [ ] #2 register() adds Doorman IP to classic Endpoints object (name=serviceName) with proxy port; idempotent
- [ ] #3 deregister() removes Doorman IP from both EndpointSlice and classic Endpoints; idempotent
- [ ] #4 ScaledApplicationRegistry calls registrar.register() after each confirmScaledDown() transition
- [ ] #5 On startup, if deployment.spec.replicas==0 and phase is ScaledDown, registrar.register() is called immediately (AC#7 of parent)
- [ ] #6 If podIp cannot be resolved from --pod-ip or POD_IP env, startup fails with a clear error
- [ ] #7 Unit test: recording EndpointRegistrar stub verifies register() is called after confirmScaledDown
- [ ] #8 IT test: k3s -- create Deployment + Service + ScalingPolicy; patch deployment to 0; assert Doorman IP in EndpointSlice + classic Endpoints; assert ScalingPolicy phase == ScaledDown
- [ ] #9 Fight-back loop: when EndpointsInformer or EndpointSliceInformer detects Doorman IP is absent (Kubernetes removed it due to selector mismatch), registry calls registrar.register() again while app is ScaledDown or ScalingUp
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
