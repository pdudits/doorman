---
id: TASK-005.09
title: K3s-based integration test infrastructure for Kubernetes operator components
status: Done
assignee:
  - Copilot
created_date: '2026-03-13 17:21'
updated_date: '2026-03-13 17:57'
labels:
  - testing
  - kubernetes
  - infrastructure
milestone: m-0
dependencies: []
parent_task_id: TASK-005
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add Testcontainers K3s module so that integration tests can run against a real, isolated Kubernetes API server without requiring a pre-existing cluster.

## Motivation
Manual tests (e.g. `TraefikServiceNameResolverTestManual`) exercise real cluster behavior but require the tester to have a live cluster with correct credentials. A k3s container gives an isolated, reproducible API server in CI and local dev without side effects.

## Approach
1. Add `testcontainers-k3s` dependency (test scope) to `pom.xml`.
2. Extend `KubernetesConfig` (or add a sibling) to accept a full kubeconfig YAML string, not just a context name — so `KubernetesClientFacade` can be constructed from the YAML emitted by `K3sContainer.getKubeConfigYaml()`.
3. Extend `KubernetesClientFacadeAccessor` with a `create(String kubeConfigYaml)` factory.
4. Create a shared JUnit 5 extension or base class that starts a single `K3sContainer` per test class (or suite) and exposes a configured `KubernetesFacade`.
5. Port `TraefikServiceNameResolverTestManual` into a proper automated integration test using the k3s container.
6. Verify the BouncyCastle PKIX workaround is in place if k3s uses elliptic curve keys.

## Notes
- `K3sContainer` is from `org.testcontainers:k3s`.
- Fabric8 can be configured via `Config.fromKubeconfig(yaml)` — no reflection needed.
- Tests using k3s should be in a separate Maven Surefire include pattern (e.g. `*IT.java`) or a separate profile to avoid slowing down the standard unit test run.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 testcontainers-k3s 1.21.3 dependency added (test scope)
- [x] #2 KubernetesConfig converted to sealed interface with Context(kubeContext) and Raw(kubeConfigYaml) variants
- [x] #3 KubernetesClientFacadeAccessor exposes createFromKubeConfigYaml(String) factory
- [x] #4 K3sClusterExtension manages K3sContainer + DoormanClusterSetup lifecycle per test class
- [x] #5 TraefikServiceNameResolverIT runs against real k3s API server without manual cluster
- [x] #6 maven-failsafe-plugin added; *IT.java runs on verify, excluded from surefire unit test run
- [ ] #7 No BouncyCastle needed — JDK 21 handles EC keys natively (AC7 rejected with explanation)
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan

### Decisions agreed
- `KubernetesConfig` → sealed interface with `Context(String kubeContext)` and `Raw(String kubeConfigYaml)` variants (consistent with `TraefikConfig`)
- JUnit 5 extensions split: `ManualInClusterExtension` (renamed from `DoormanClusterExtension`) for real-cluster manual tests; new `K3sClusterExtension` that owns the container and composes with a refactored cluster setup helper
- Maven: `maven-failsafe-plugin` with `*IT.java` suffix for k3s tests; `*TestManual` stays Surefire-excluded as before
- No BouncyCastle — JDK 21 handles EC keys natively

### Step-by-step

#### 1. `KubernetesConfig` → sealed interface
Convert `record KubernetesConfig(String kubeContext)` to:
```java
public sealed interface KubernetesConfig {
    record Context(String kubeContext) implements KubernetesConfig {}
    record Raw(String kubeConfigYaml) implements KubernetesConfig {}
}
```
Update `KubernetesClientFacade` to switch on the variant:
- `Context` → `Config.autoConfigure(ctx.kubeContext())`
- `Raw` → `Config.fromKubeconfig(raw.kubeConfigYaml())`

Update `KubernetesClientFacadeAccessor`:
- existing `create(String kubeContext)` → uses `new KubernetesConfig.Context(kubeContext)`
- new `createFromKubeConfigYaml(String yaml)` → uses `new KubernetesConfig.Raw(yaml)`

Update `CliArgs`/`ConfigProvider` (wherever `KubernetesConfig` is constructed from CLI) to use `new KubernetesConfig.Context(...)`.

#### 2. Extract `DoormanClusterSetup` helper
Extract the CRD + namespace idempotent setup/teardown logic from `DoormanClusterExtension` into a non-extension class `DoormanClusterSetup` (plain class, no JUnit dependency). Both extensions will delegate to it.

#### 3. Rename `DoormanClusterExtension` → `ManualInClusterExtension`
Drop-in rename. Update `ScalingPolicyCRDTestManual` and `TraefikServiceNameResolverTestManual` to reference the new name.

#### 4. Add `testcontainers-k3s` dependency
```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>k3s</artifactId>
    <version>1.21.x</version>
    <scope>test</scope>
</dependency>
```
Pin to the same BOM version as the rest of testcontainers (none currently — pick latest stable).

#### 5. `K3sClusterExtension`
Implements `BeforeAllCallback`, `AfterAllCallback`. Owns a `K3sContainer` (started in `beforeAll`, stopped in `afterAll`). After start, builds a `KubernetesClient` via `KubernetesClientFacadeAccessor.createFromKubeConfigYaml(k3s.getKubeConfigYaml())`, then delegates CRD + namespace setup to `DoormanClusterSetup`. Exposes `client()`, `namespace()`, and `facade()`.

#### 6. Configure `maven-failsafe-plugin`
Add `maven-failsafe-plugin` to `pom.xml`, includes `**/*IT.java`. Bind to `integration-test` + `verify` phases.

#### 7. `TraefikServiceNameResolverIT`
Port `TraefikServiceNameResolverTestManual` to `TraefikServiceNameResolverIT`, using `@RegisterExtension K3sClusterExtension`. Same assertions, now fully automated.

### Files changed
- `KubernetesConfig.java` (converted to sealed interface)
- `KubernetesClientFacade.java` (switch on variant)
- `KubernetesClientFacadeAccessor.java` (new factory method)
- `CliArgs.java` / `ConfigProvider.java` (construct `KubernetesConfig.Context`)
- `DoormanClusterExtension.java` → `ManualInClusterExtension.java` (rename)
- `DoormanClusterSetup.java` (new — extracted helper)
- `ScalingPolicyCRDTestManual.java` (update extension reference)
- `TraefikServiceNameResolverTestManual.java` (update extension reference)
- `K3sClusterExtension.java` (new)
- `TraefikServiceNameResolverIT.java` (new)
- `pom.xml` (k3s dependency + failsafe plugin)
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## K3s integration test infrastructure — Implementation Summary

### What Changed

**`KubernetesConfig`** — converted from `record KubernetesConfig(String kubeContext)` to a sealed interface with two variants: `Context(String kubeContext)` and `Raw(String kubeConfigYaml)`. Consistent with the `TraefikConfig` pattern.

**`KubernetesClientFacade`** — switches on `KubernetesConfig` variant: `Context` → `Config.autoConfigure(ctx)`, `Raw` → `Config.fromKubeconfig(yaml)`.

**`ConfigProvider`** — updated to construct `KubernetesConfig.Context`.

**`KubernetesClientFacadeAccessor`** — added `createFromKubeConfigYaml(String)` factory.

**`DoormanClusterSetup`** (new) — plain helper (no JUnit dependency) encapsulating idempotent CRD + namespace setup/teardown. Shared by both extensions.

**`DoormanClusterExtension` → `ManualInClusterExtension`** — renamed to signal it targets a real pre-existing cluster. Delegates lifecycle to `DoormanClusterSetup`. Both manual tests updated.

**`K3sClusterExtension`** (new, `io.zeromagic.doorman.k3s`) — starts a `K3sContainer`, builds a `KubernetesClient` via `Config.fromKubeconfig()`, delegates CRD + namespace setup to `DoormanClusterSetup`. Exposes `client()`, `namespace()`, `facade()`.

**`TraefikServiceNameResolverIT`** (new) — automated port of the manual test. Same 5-step assertions, now fully automated against k3s.

**`pom.xml`** — `testcontainers:k3s:1.21.3` (test scope) + `maven-failsafe-plugin` bound to `integration-test` + `verify` phases (picks up `*IT.java`).

### AC7 — BouncyCastle
Rejected: JDK 21 supports elliptic curve keys natively. The Testcontainers docs caveat predates JDK 17. No exception observed; add only if a PKIX failure surfaces.

### Run integration tests
```
mvn verify
```
Unit tests only: `mvn test`
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [x] #3 Code is compiling and unit test verifies its relevant functionality
- [x] #4 An integration test is written
- [x] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
