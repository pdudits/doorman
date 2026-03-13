---
id: TASK-005.05
title: 'TraefikServiceNameResolver: resolve Traefik service label from Ingress'
status: To Do
assignee: []
created_date: '2026-03-13 11:46'
labels:
  - traffic
  - kubernetes
milestone: m-0
dependencies:
  - TASK-005.01
references:
  - src/main/java/io/zeromagic/doorman/traffic/
  - src/main/java/io/zeromagic/doorman/crd/
parent_task_id: TASK-005
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Implement `TraefikServiceNameResolver` \u2014 resolves the Traefik internal service label string from a Kubernetes Ingress, needed by `IdleDetector` to look up the right counter in Prometheus metrics.

Traefik internally names a service as `{namespace}-{serviceName}-{port}@kubernetes`. The port comes from the backend definition in the Kubernetes Ingress named `ScalingPolicy.spec.ingressName`.

Depends on task-005.01 (ingressName field on ScalingPolicySpec).

Context: Lives in `src/main/java/io/zeromagic/doorman/traffic/`. Uses Fabric8 `KubernetesClient` to fetch the Ingress object. The Fabric8 model for Ingress is in `io.fabric8.kubernetes.api.model.networking.v1`.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 TraefikServiceNameResolver resolves the Traefik internal service label for a given (namespace, serviceName) pair
- [ ] #2 Resolution algorithm: GET the Kubernetes Ingress named ScalingPolicy.spec.ingressName in the same namespace; find the backend port for the service; build the label as '{namespace}-{serviceName}-{port}@kubernetes'
- [ ] #3 Results are cached after first successful lookup (avoids repeated Ingress API calls)
- [ ] #4 Cache is invalidated when the associated ScalingPolicy is updated (resolver implements onUpdated for ScalingPolicy events, or accepts an explicit invalidate call)
- [ ] #5 Returns Optional.empty() and logs a warning if the Ingress is not found or does not reference the service
- [ ] #6 Unit tests with fake/stub Ingress objects (no real Kubernetes cluster required); covers: happy path, ingress not found, service not in ingress rules
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
