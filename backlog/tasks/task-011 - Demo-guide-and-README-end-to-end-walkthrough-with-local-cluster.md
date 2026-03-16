---
id: TASK-011
title: 'Demo guide and README: end-to-end walkthrough with local cluster'
status: To Do
assignee: []
created_date: '2026-03-12 11:28'
updated_date: '2026-03-16 15:21'
labels:
  - documentation
  - demo
milestone: m-0
dependencies: []
priority: low
ordinal: 3000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Write the user-facing documentation and demo guide. The goal is that someone unfamiliar with the project can follow this guide end-to-end and see Doorman working on a local kind/minikube cluster.

Key sections:
1. Overview (what it does, what it needs)
2. Prerequisites (kind/minikube, kubectl, Traefik deployed)
3. Installation (apply manifests)
4. Creating your first ScalingPolicy
5. Watching it work (demo walkthrough)
6. Local development / port-forward instructions
7. Troubleshooting
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 README explains what Doorman is and how it works (1-page overview)
- [ ] #2 Step-by-step guide to running Doorman against a local cluster (kind or minikube): install Traefik, apply CRD, apply RBAC, deploy a test app, create a ScalingPolicy, observe scale-to-zero and scale-up
- [ ] #3 Instructions for `kubectl port-forward` to expose Doorman's proxy port locally for testing without being inside the cluster
- [ ] #4 A sample ScalingPolicy YAML is included in the guide with annotations explaining each field
- [ ] #5 Troubleshooting section: how to check ScalingPolicy status, how to read Doorman logs
- [ ] #6 Note on Traefik service name format (`<namespace>-<service>-<port>@kubernetesIngress`) and how to find it in Traefik dashboard or metrics
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
