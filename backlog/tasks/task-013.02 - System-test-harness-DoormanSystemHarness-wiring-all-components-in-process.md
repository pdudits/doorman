---
id: TASK-013.02
title: 'System test harness: DoormanSystemHarness wiring all components in-process'
status: To Do
assignee: []
created_date: '2026-03-13 23:38'
labels:
  - system-test
  - infrastructure
milestone: m-0
dependencies:
  - TASK-013.01
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create `DoormanSystemHarness` — a test helper that wires all Doorman components in-process against a real k3s cluster via `TraefikK3sExtension`. Replaces avaje DI with explicit construction for system tests.

**Components wired by harness:**
- `KubernetesClientFacade(ext.client(), doormanConfig)`
- `EndpointRegistrar`, `DeploymentScaler`
- `ScaledApplicationRegistry`
- `IngressRouteIndex`
- `ProxyServer` on random port (port 0)
- All 4 informers (`ScalingPolicyInformer`, `DeploymentInformer`, `EndpointsInformer`, `EndpointSliceInformer`)
- `IdleDetector` with real Traefik metrics URL from `ext.traefikMetricsUrl()`, short poll interval (2s)

**Docker host IP for endpoint registration**: Doorman proxy runs in test JVM. Traefik (inside k3s) must route to it. Use `Testcontainers.exposeHostPorts(proxyPort)` + host IP from Docker gateway. Pass as `podIp` in `DoormanConfig`.

**Test resource helpers:**
- `deployNginx(namespace, name, replicas)` — Deployment + Service
- `createIngress(namespace, host, path, serviceName, port)`
- `createScalingPolicy(namespace, name, serviceName, deploymentName, ingressName, targetReplicas)`
- `awaitPodReady(namespace, labelSelector)`
- `awaitScalingPolicyPhase(namespace, name, phase)`
- `getCurrentReplicas(namespace, deploymentName)`

**Key files:**
- new `src/test/java/io/zeromagic/doorman/k3s/DoormanSystemHarness.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 DoormanSystemHarness starts and stops cleanly
- [ ] #2 Proxy port is reachable from test JVM after start()
- [ ] #3 Kubernetes informers receive events from k3s
- [ ] #4 Doorman endpoint IP is reachable from inside k3s container so Traefik can route to it
- [ ] #5 idleTimeout and poll interval are configurable for fast tests
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
