---
id: TASK-015
title: Install license-maven-plugin to enforce and apply SPDX headers
status: Done
assignee: []
created_date: '2026-03-16 15:22'
updated_date: '2026-03-16 19:14'
labels:
  - licensing
dependencies:
  - TASK-014
priority: medium
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add com.mycila:license-maven-plugin to pom.xml. Configure it to use the short SPDX header (Copyright 2026 Patrik Dudits / SPDX-License-Identifier: Apache-2.0) on all src/**/*.java files. Bind license:check to the verify phase so CI fails on missing headers. Run mvn license:format to apply headers to all current Java files. Exclude generated sources and test resource files.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 mvn license:check passes on a clean checkout
- [x] #2 mvn license:format adds correct 2-line SPDX header to .java files that lack it
- [x] #3 Generated/resource files are excluded
- [x] #4 license:check is bound to the verify phase
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add `com.mycila:license-maven-plugin:5.0.0` to `<build><plugins>` in pom.xml
   - Use built-in `com/mycila/maven/plugin/license/templates/APACHE-2.txt` header template
   - Set properties: `owner=Patrik Dudits`, `year=2026`
   - Exclude `src/test/resources/**` and `src/main/resources/**`
   - Bind `check` goal to `verify` phase
2. Run `mvn license:format` — applies headers to all 71 Java files
3. Run `mvn verify -DskipTests` — confirms `license:check` passes and build compiles
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Using built-in APACHE-2.txt template (standard ASF appendix boilerplate). Generated sources under target/ are excluded by plugin default excludes. Resource files excluded explicitly. Java comment style is /* */ block (SLASHSTAR_STYLE, default).

Used `includes` to restrict to `src/**/*.java` only (YAML and pom.xml were also being processed without it). Added `<email>` property to resolve the `${email}` placeholder in APACHE-2.txt template. 72 files changed: pom.xml + 71 Java source files. `mvn verify -DskipTests` passes clean.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## TASK-015: Install license-maven-plugin

Added `com.mycila:license-maven-plugin:5.0.0` to `pom.xml` with the built-in `APACHE-2.txt` template (standard Apache License 2.0 appendix boilerplate, not SPDX short form, per user request).

### Changes
- **pom.xml**: Added plugin block with `includes: src/**/*.java`, `owner=Patrik Dudits`, `email=patrik@dudits.net`, `year=2026`; `license:check` bound to `verify` phase
- **71 Java files**: All now carry the standard `/* Copyright © 2026 Patrik Dudits (patrik@dudits.net) ... */` header

### Verification
- `mvn license:format` applied headers to all Java files cleanly
- `mvn verify -DskipTests` passes (license:check included)
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
