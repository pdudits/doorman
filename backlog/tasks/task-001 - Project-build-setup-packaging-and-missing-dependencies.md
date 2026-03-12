---
id: TASK-001
title: 'Project build setup: packaging and missing dependencies'
status: In Progress
assignee: []
created_date: '2026-03-12 11:25'
updated_date: '2026-03-12 12:28'
labels:
  - build
  - setup
milestone: m-0
dependencies: []
priority: high
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add the remaining build and runtime wiring so Doorman compiles into a production-friendly artifact.

Changes include:
- Picocli code generation dependency so the CLI reflection metadata and help text can be produced at compile time.
- Logback Classic with a JSON console encoder configured programmatically via a `Configurator`, so structured logging is available without XML.
- `junit-jupiter-engine` so Surefire can execute any future tests.
- Maven Shade Plugin configuration that emits `doorman-shaded.jar` with `Main-Class` set and merged SPI resources.

Also make `--help`/`--version` work gracefully through picocli’s standard options.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 mvn package produces a runnable fat JAR (java -jar target/doorman-*.jar --help works)
- [ ] #2 avaje annotation processors generate the DI wiring at compile time (no runtime reflection errors)
- [x] #3 All declared dependencies resolve without conflict
- [x] #4 picocli annotation processor generates reflection config if needed
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Implementation Plan for TASK-001 (revised)
1. Declare `picocli-codegen`, `logback-classic`, and `junit-jupiter-engine` in the POM alongside the existing DI and Fabric8 dependencies.
2. Add a programmatic Logback `Configurator` implementation that wires up a console appender with `JsonEncoder`, then register it via `META-INF/services` so the JSON output is active at runtime.
3. Configure the Maven Shade Plugin to build `target/doorman-shaded.jar` with the correct `Main-Class` and merged `META-INF/services/` entries.
4. Enhance the picocli `CliArgs` declaration so `--help` and `--version` are supported and the CLI exits cleanly when they’re requested.
5. Verify the build with `mvn clean package` and confirm `java -jar target/doorman-shaded.jar --help` prints usage and exits.
<!-- SECTION:PLAN:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
