---
name: testing
description: Guides for testing patterns in the project, junit, mockito, manual test, k3s integration tests, planning implementation of tests
---

# Testing Guide

These are the guidelines to hold on to when generating and running tests in the project

## Avoid mocking

Do not use Mockito or similar libraries. Rather create a test double. Design the code in such way that test double is easy to provide.

## Use assertj

AssertJ provides nicer error messages and is more expressive in testing.

## Construct manually, use access classes

Unless the manual construction of the classes becomes too hard, don't rely on avajo testing capabilities.
If the class you need to use is not public and is in different package consider creating Accessor test class:

```java
package pkg.other;

class Component implements PublicInterface {
  Component() {
  }
}
```

create a class in src/test/java:
```java
package pkg.other;

public class ComponentAccessor() {
    private ComponentAccessor() {}
    
    public static PublicInterface createComponent() {
        return new Component();
    }
}
```

and in your test
```java
package pkg.undertest;

class ATest {
  private PublicInterface component = ComponentAccessor.createComponent();
}
```

## Manual Tests

When there's need to work with real cluster and perform potentially dangerous operations or when special credentials are required
create a manual test and let user run it in appropriate security context.

For example:
1. The `ScalingPolicyCRDTestManual` test checks whether the `ScalingPolicy` CRD exists, applies the manifest from `deploy/scalingpolicy-crd.yaml` if needed, creates namespace `0000-0000`, and instantiates a sample `ScalingPolicy`.
2. Because the test is suffixed with `TestManual`, Surefire will skip it. Suggest user to run it manually (e.g., `mvn -Dtest=ScalingPolicyCRDTestManual test`) when you want to exercise the CRD and sample resource in a real cluster.
3. Do not run any manual tests yourself, these are usually dangerous and should be completed by human

## K3s-based Integration Tests

When a test needs a real Kubernetes API server but should run automatically (e.g. in CI), use the
`K3sClusterExtension` backed by Testcontainers. These tests:

- Are placed in `src/test/java` and named with the `*IT` suffix so maven-failsafe-plugin picks them
  up (Surefire skips `*IT` by default).
- Start an isolated k3s cluster per test class — one `K3sContainer` per extension instance.
- The CRD and a dedicated namespace are applied automatically by `DoormanClusterSetup` inside
  `K3sClusterExtension`; both are cleaned up in `afterAll`.

### Usage pattern

```java
class MyComponentIT {

    @RegisterExtension
    static final K3sClusterExtension K3S = new K3sClusterExtension("my-namespace");

    @Test
    void myTest() {
        var ns     = K3S.namespace();   // the isolated namespace
        var client = K3S.client();      // raw Fabric8 KubernetesClient
        var facade = K3S.facade();      // KubernetesFacade (implements all four Doorman interfaces)

        // create resources via client, build components under test with facade, assert outcomes
    }
}
```

### Mixing real and stub collaborators

`KubernetesFacade` implements `ScalingPolicyStatusPatcher`, `DeploymentStateReader`,
`ServiceScaler`, and `EndpointRegistrar`. Use `K3S.facade()` for the interfaces you want to
exercise for real (e.g. `ScalingPolicyStatusPatcher`), and pass inline no-op anonymous classes
for interfaces whose side-effects are irrelevant to the test:

```java
var registry = new ScaledApplicationRegistry(
    facade,                                              // real status patcher
    new ServiceScaler() {
        @Override public void scaleUp(String n, String d, int r) {}
        @Override public void scaleDown(String n, String d) {}
    },
    new EndpointRegistrar() {
        @Override public void register(String n, String s) {}
        @Override public void deregister(String n, String s) {}
    },
    (ns, dep) -> Optional.of(new DeploymentStateReader.DeploymentState(1, 1)) // stub
);
```

### maven-failsafe working directory caveat

Failsafe forks the JVM with `target/` as the working directory, **not** `${project.basedir}`.
Never load files via `new File("relative/path")` in IT tests — always load from the classpath:

```java
getClass().getClassLoader().getResourceAsStream("my-resource.yaml")
```

If the resource lives outside `src/test/resources`, add its directory as a `<testResource>` in
`pom.xml` (see the `deploy/` entry for `scalingpolicy-crd.yaml` as an example).
