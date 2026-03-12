---
id: TASK-001
title: 'Project build setup: packaging and missing dependencies'
status: To Do
assignee: []
created_date: '2026-03-12 11:25'
labels:
  - build
  - setup
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
The current pom.xml has the core DI and Kubernetes client dependencies but is missing several needed for the full feature set. This task ensures the project compiles into a runnable fat JAR and all necessary libraries are declared.

Missing dependencies to add:
- `avaje-config` (avaje configuration / env var binding)
- `io.fabric8:crd-generator-apt` or `fabric8-crd-annotations` for CRD model generation (optional — may hand-write CRD YAML instead)
- Maven Shade Plugin (or Assembly Plugin) to produce a single executable JAR
- Annotation processor configuration for avaje-inject-generator and picocli-codegen

Also verify that the annotation processors for avaje-inject and picocli are properly wired in the Maven compiler plugin (`annotationProcessorPaths`).
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 mvn package produces a runnable fat JAR (java -jar target/doorman-*.jar --help works)
- [ ] #2 avaje annotation processors generate the DI wiring at compile time (no runtime reflection errors)
- [ ] #3 All declared dependencies resolve without conflict
- [ ] #4 picocli annotation processor generates reflection config if needed
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
