---
id: TASK-013.01
title: 'Traefik k3s extension: expose ports, enable Prometheus metrics, wait for ready'
status: Done
assignee: []
created_date: '2026-03-13 23:38'
updated_date: '2026-03-14 08:12'
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
- [x] #1 TraefikK3sExtension provides traefikHttpPort() and traefikMetricsUrl()
- [x] #2 HTTP request through Traefik to a real nginx pod returns 200
- [x] #3 Metrics URL returns OpenMetrics text with traefik_ counters
- [x] #4 Traefik pod is fully ready before any test methods run
- [x] #5 Extension composes/extends K3sClusterExtension without duplicating cluster lifecycle
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan (as executed)\n\n### Changes to K3sClusterExtension\n- Added `protected void configureContainer(K3sContainer container)` — no-op hook called between `new K3sContainer()` and `container.start()`, allowing subclasses to configure ports, copy files, etc.\n- Added `protected int getMappedPort(int containerPort)` — delegates to `container.getMappedPort()` for subclass use\n\n### New file: traefik-metrics.yaml (test resource)\nHelmChartConfig enabling Prometheus metrics on entrypoint `metrics` at port 9100 with `hostPort: 9100`. Applied via K8s API (not file copy) because `withCopyFileToContainer` copies AFTER container start — too late for initial Helm chart install.\n\n### New class: TraefikK3sExtension\n`configureContainer()` override:\n- `container.setCommand(\"server\", \"--tls-san=\" + container.getHost())` — removes `--disable=traefik` which K3sContainer hardcodes\n- `container.addExposedPort(80)` + `addExposedPort(9100)` — `addExposedPort` not `withExposedPorts` to preserve existing 6443/8443\n- `container.withLogConsumer(new Slf4jLogConsumer(LOG).withPrefix(\"k3s\"))` — for diagnostics\n\n`beforeAll()` override:\n1. `super.beforeAll(context)` — starts cluster, CRD, namespace\n2. `enableTraefikMetrics()` — `client().load(yaml).create()` the HelmChartConfig; Helm controller reconciles and restarts Traefik with metrics config\n3. `awaitTraefikReady()` — polls pods with label `app.kubernetes.io/name=traefik` (excludes helm-install Job pods and svclb pods), 240s timeout\n\nExposes: `traefikHttpPort()` → `getMappedPort(80)`, `traefikMetricsUrl()` → metrics URL on `getMappedPort(9100)`\n\n### New class: TraefikSmokeIT\nBackend: `mendhak/http-https-echo:latest` (reflects method + body as JSON — chosen over a static server so future E2E tests can verify 307 redirect preserves method and body)\n\n- `echo_server_accessible_through_traefik()`: deploy Deployment + Service (port 80 → targetPort 8080) + Ingress for `echo-smoke.test`; wait for pod ready; retry POST `{\"hello\":\"doorman\"}`; assert response contains `POST` and `doorman`\n- `traefik_prometheus_metrics_available()`: GET `traefikMetricsUrl()`; assert 200 + body contains `traefik_`
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## k3s ServiceLB (klipper-lb) learnings

For each LoadBalancer Service, k3s creates a DaemonSet in `kube-system` — the `svclb-traefik-*` pods. These bind to the **hostPort** (80, 443) on the k3s node (= the Docker container), forwarding traffic to the Traefik ClusterIP Service.

Real traffic path: Docker host mapped port → k3s container:80 → svclb-traefik (hostPort) → Traefik ClusterIP → Traefik pod → backend

Waiting for `app.kubernetes.io/name=traefik` pod Ready is sufficient in practice — `svclb-traefik` starts fast enough that it is always ready by the time tests make HTTP requests. We do NOT need to wait for it explicitly.

`--disable=servicelb` would break the Traefik `LoadBalancer` service and should never be added to the k3s command.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## Implemented

**Modified** `K3sClusterExtension`:
- Added `protected void configureContainer(K3sContainer container)` hook (no-op) called before `container.start()` — allows subclasses to expose ports, copy files, etc.
- Added `protected int getMappedPort(int containerPort)` delegating to the container — gives subclasses access to mapped host ports

**Created** `src/test/resources/traefik-metrics.yaml`:
- `HelmChartConfig` enabling Traefik Prometheus metrics on entrypoint `metrics` at port 9100 with `hostPort: 9100`
- Copied into `/var/lib/rancher/k3s/server/manifests/` before k3s starts → Traefik gets metrics from initial Helm install; no restart needed

**Created** `TraefikK3sExtension extends K3sClusterExtension`:
- Overrides `configureContainer()`: `.withExposedPorts(80, 9100)` + copies traefik-metrics.yaml
- Overrides `beforeAll()`: calls super then `awaitTraefikReady()` (polls kube-system pods with label `app.kubernetes.io/name=traefik`, 2 min timeout)
- Exposes `traefikHttpPort()` → `getMappedPort(80)` and `traefikMetricsUrl()` → metrics URL on mapped port 9100

**Created** `TraefikSmokeIT`:
- Backend: `mendhak/http-https-echo:latest` (echoes method+body as JSON — reusable for E2E method/body preservation checks)
- `echo_server_accessible_through_traefik()`: deploy echo + Service + Ingress for `echo-smoke.test`; wait for pod ready; retry POST with `{"hello":"doorman"}` body; verify response contains `POST` and `doorman`
- `traefik_prometheus_metrics_available()`: GET metrics URL; verify 200 + `traefik_` prefix

## Bugs found and fixed during test execution

1. **`withExposedPorts` replaces K3sContainer's default ports** — calling `container.withExposedPorts(80, 9100)` replaced the existing port 6443 (Kubernetes API server), crashing cluster startup with `Requested port (6443) is not mapped`. Fixed by using `container.addExposedPort()` instead.

2. **K3sContainer disables Traefik by default** — `K3sContainer` hardcodes `setCommand("server", "--disable=traefik", ...)`. Fixed by overriding with `setCommand("server", "--tls-san=" + container.getHost())`.

3. **`withCopyFileToContainer` runs AFTER container start** — files are copied in `containerIsStarted()`, which is after k3s has already processed its initial manifests. Changed approach: apply `HelmChartConfig` via Kubernetes API after cluster starts (`client().load(yaml).create()`), letting the Helm controller reconcile and restart Traefik with metrics enabled.

4. **Readiness check matched Job pods** — filtering by pod name containing `traefik` included `helm-install-traefik-*` Job pods (which complete with exit 0 and are never `Ready=True`) causing `allReady` to be permanently false. Fixed by using label selector `app.kubernetes.io/name=traefik` which matches only the actual Traefik deployment pod.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
