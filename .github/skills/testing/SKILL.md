---
name: testing
description: Guides for manual and exploratory testing workflows in the repo.
---

## Manual Tests

When there's need to work with real cluster and perform potentially dangerous operations or when special credentials are required
create a manual test and let user run it in appropriate security context.

For example:
1. The `ScalingPolicyCRDTestManual` test checks whether the `ScalingPolicy` CRD exists, applies the manifest from `deploy/scalingpolicy-crd.yaml` if needed, creates namespace `0000-0000`, and instantiates a sample `ScalingPolicy`.
2. Because the test is suffixed with `TestManual`, Surefire will skip it. Suggest user to run it manually (e.g., `mvn -Dtest=ScalingPolicyCRDTestManual test`) when you want to exercise the CRD and sample resource in a real cluster.
3. Do not run any manual tests yourself, these are usually dangerous and should be completed by human
