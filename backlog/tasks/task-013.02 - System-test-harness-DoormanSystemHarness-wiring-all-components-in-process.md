---
id: TASK-013.02
title: 'System test harness: DoormanSystemHarness wiring all components in-process'
status: Done
assignee: []
created_date: '2026-03-13 23:38'
updated_date: '2026-03-14 09:15'
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
- [x] #1 DoormanSystemHarness starts and stops cleanly
- [x] #2 Proxy port is reachable from test JVM after start()
- [x] #3 Kubernetes informers receive events from k3s
- [x] #4 Doorman endpoint IP is reachable from inside k3s container so Traefik can route to it
- [x] #5 idleTimeout and poll interval are configurable for fast tests
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Approach
Use `BeanScope.builder()` with two external substitutions:
1. `CliArgs` — populated via `new CommandLine(args).parseArgs(...)` (same as Main.java)
2. `KubernetesConfig.Raw(ext.kubeConfigYaml())` — avaje expected to wrap into Optional<KubernetesConfig>

All other beans (KubernetesClientFacade, ScaledApplicationRegistry, 4 informers, ProxyServer, IdleDetector) wired by avaje as in production.

### Step 1 — K3sClusterExtension accessors
Add `kubeConfigYaml()` and `containerGatewayIp()` public methods.

### Step 2 — DoormanSystemHarness
Constructor: `(TraefikK3sExtension ext)` and `(TraefikK3sExtension ext, String idleTimeout, String pollInterval)`
- beforeAll: find free port → `Testcontainers.exposeHostPorts(port)` → parse CliArgs via Picocli → build BeanScope
- afterAll: `scope.close()`
- Accessors: `proxyPort()`, `scope()`
- Helpers: `deployEchoApp`, `createIngress`, `createScalingPolicy`, `awaitPodReady`, `awaitScalingPolicyPhase`, `getCurrentReplicas`

### Step 3 — DoormanHarnessIT smoke test
5 tests covering ACs 1-5. Uses `@RegisterExtension` with K3S declared first, HARNESS second.

### Key uncertainty
Whether avaje wraps externally-registered `KubernetesConfig` bean into `Optional<KubernetesConfig>` for KubernetesClientFacade — smoke test verifies this.
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
## Resolved: host.testcontainers.internal not found in k3s /etc/hosts

Root cause: K3sContainer started without Docker's host-gateway extra host entry. Testcontainers' `exposeHostPorts()` SSH tunnel approach doesn't apply to K3sContainer.

Fix: Added `container.withExtraHost("host.testcontainers.internal", "host-gateway")` in `K3sClusterExtension.beforeAll()` before `container.start()`. Docker resolves `host-gateway` to `192.168.65.254` on Mac Docker Desktop — the correct reachable IP for host ports.

## Resolved: nc not available in k3s container

k3s container image has no `nc`. Available tools: `wget`, `telnet` (busybox).

Fixed AC#4 to use `wget` TCP probe. Busybox wget exits 4 on network failure, 0/1 on success/server-error. Used `[ $? -ne 4 ] && echo REACHABLE || echo UNREACHABLE`.

## Resolved: busybox wget exit code mismatch

Busybox wget exits with 1 (not 8) for HTTP server errors (e.g., 404). GNU wget convention (exit 8) doesn't apply. Fixed by checking `$? -ne 4` (network failure) instead of checking for specific success codes.

## AC#4 revised: Traefik routing test (replaces wget probe)

Instead of `wget` from inside the k3s container, AC#4 now validates the full routing path from the test JVM:

1. Create headless ClusterIP Service + manual Endpoints pointing to `podIp:proxyPort` (same mechanism as Doorman's own registration)
2. Create Ingress for `system.test` → that service
3. HTTP GET from test JVM to `http://localhost:traefikHttpPort/` with `Host: system.test`

Response from Doorman proxy (404, not 502/503) confirms: Traefik resolved the endpoint, routed to `podIp:proxyPort`, Doorman proxy answered.

Required `jdk.httpclient.allowRestrictedHeaders=host` system property in maven-failsafe-plugin config — Java HttpClient blocks the Host header by default.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## TASK-013.02: DoormanSystemHarness — all 5/5 smoke tests passing\n\n### What changed\n\n**New files:**\n- `src/test/java/io/zeromagic/doorman/k3s/DoormanSystemHarness.java` — JUnit 5 extension wiring the full avaje `BeanScope` in-process against a live k3s cluster. Only 2 external beans substituted: `CliArgs` (via Picocli parsing) and `KubernetesConfig.Raw(kubeConfigYaml)`. All other components wired by avaje exactly as in production.\n- `src/test/java/io/zeromagic/doorman/k3s/DoormanHarnessIT.java` — 5-test smoke suite covering all ACs.\n\n**Modified files:**\n- `K3sClusterExtension.java`: added `kubeConfigYaml()`, `containerGatewayIp()`, `execInContainer()` accessors. Added `withExtraHost(\"host.testcontainers.internal\", \"host-gateway\")` before container start.\n- `pom.xml`: added `jdk.httpclient.allowRestrictedHeaders=host` to failsafe plugin (required for setting Host header in Java HttpClient).\n- `.github/skills/backlog/SKILL.md`: created backlog skill.\n\n### Key technical findings\n\n1. **avaje Optional wrapping confirmed**: registering `KubernetesConfig` externally causes avaje to skip the factory and inject `Optional.of(externalBean)` automatically.\n\n2. **AC#4 — full Traefik routing path**: creates a headless Service + manual Endpoints pointing to `podIp:proxyPort`, Ingress for `system.test`, then HTTP GET from test JVM via Traefik using `Host: system.test`. Response from Doorman (404, not 502/503) confirms the full path works. This is the same mechanism Doorman uses when registering itself as an endpoint.\n\n3. **Mac Docker Desktop host connectivity**: `host-gateway` = `192.168.65.254`. K3sContainer needs `withExtraHost(\"host.testcontainers.internal\", \"host-gateway\")` so `resolveTestcontainersHostIp()` can read the IP for `podIp`.\n\n4. **Java HttpClient Host header**: restricted by default — must set `jdk.httpclient.allowRestrictedHeaders=host` system property.\n\n### Tests\nAll 5 smoke tests pass in ~27s on Mac Docker Desktop with k3s v1.31.5-k3s1 (Traefik v2)."
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
