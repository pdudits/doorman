---
name: testing
description: Guides for testing patterns in the project, junit, mockito, manual test
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
