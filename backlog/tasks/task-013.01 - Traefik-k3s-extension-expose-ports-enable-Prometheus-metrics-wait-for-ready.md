---
id: TASK-013.01
title: 'Traefik k3s extension: expose ports, enable Prometheus metrics, wait for ready'
status: To Do
assignee: []
created_date: '2026-03-13 23:38'
labels:
  - system-test
  - traefik
  - k3s
  - infrastructure
milestone: m-0
dependencies: []
parent_task_id: TASK-013
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Create `TraefikK3sExtension` (extends or wraps `K3sClusterExtension`) that configures k3s to expose Traefik and enable its Prometheus metrics endpoint. This is the base infrastructure for all E2E system tests.

**Traefik is already running** in the existing k3s container (default k3s does NOT disable it), but its ports are not exposed and metrics are not enabled.

**Scope:**
1. New `TraefikK3sExtension` class extending `K3sClusterExtension`:
   - Override container construction to add `.withExposedPorts(80, 9100)` (Traefik HTTP + metrics)
   - After cluster starts, apply `HelmChartConfig` via Fabric8 to enable Prometheus metrics on port 9100:
     ```yaml
     apiVersion: helm.cattle.io/v1
     kind: HelmChartConfig
     metadata:
       name: traefik
       namespace: kube-system
     spec:
       valuesContent: |
         metrics:
           prometheus:
             entryPoint: metrics
         ports:
           metrics:
             port: 9100
             expose:
               default: true
             exposedPort: 9100
     ```
   - Wait for Traefik Deployment in `kube-system` to be ready after metrics config applied (pods must restart to pick up new config)
   - Expose `traefikHttpPort()` → `container.getMappedPort(80)`
   - Expose `traefikMetricsUrl()` → `"http://localhost:" + container.getMappedPort(9100) + "/metrics"`

2. Verify in a smoke test (`TraefikSmokeIT`):
   - Deploy nginx Deployment + Service + Ingress into test namespace
   - Wait for nginx pod ready
   - Send HTTP GET to `http://localhost:{traefikHttpPort}/` with `Host: test-svc.test` header → expect 200 response from nginx
   - Fetch metrics URL → response contains `traefik_` metric names

**Key implementation note**: `K3sContainer` stores port mappings at container start time. The `.withExposedPorts(80, 9100)` call must happen before `container.start()`. If `K3sClusterExtension` instantiates the container in a way that can't be easily overridden, use composition instead of inheritance.

**Wait strategy for Traefik restart**: After applying HelmChartConfig, wait for the Traefik `Deployment` in kube-system to have `readyReplicas == 1` using Fabric8's `waitUntilReady`. This takes ~30-60 seconds total.

**Key files:**
- new `src/test/java/io/zeromagic/doorman/k3s/TraefikK3sExtension.java`
- new `src/test/java/io/zeromagic/doorman/k3s/TraefikSmokeIT.java`
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TraefikK3sExtension provides traefikHttpPort() and traefikMetricsUrl()
- [ ] #2 HTTP request through Traefik to a real nginx pod returns 200
- [ ] #3 Metrics URL returns OpenMetrics text with traefik_ counters
- [ ] #4 Traefik pod is fully ready before any test methods run
- [ ] #5 Extension composes/extends K3sClusterExtension without duplicating cluster lifecycle
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
