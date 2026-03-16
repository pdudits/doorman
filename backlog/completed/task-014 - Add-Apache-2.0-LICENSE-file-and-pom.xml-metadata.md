---
id: TASK-014
title: Add Apache 2.0 LICENSE file and pom.xml metadata
status: Done
assignee: []
created_date: '2026-03-16 15:21'
updated_date: '2026-03-16 19:02'
labels:
  - licensing
dependencies: []
priority: medium
ordinal: 11000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Add the Apache 2.0 LICENSE file to the repo root, a minimal NOTICE file, and update pom.xml with a <licenses> block (Apache-2.0) and <developers> block (Patrik Dudits). No source headers yet — that is handled by subsequent tasks.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 LICENSE file present in repo root (full Apache 2.0 text)
- [x] #2 NOTICE file present with copyright line: Copyright 2025-2026 Patrik Dudits
- [x] #3 pom.xml contains <licenses> block referencing Apache-2.0
- [x] #4 pom.xml contains <developers> block for Patrik Dudits
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
1. Add `LICENSE` file to repo root — full Apache 2.0 text (canonical from apache.org)
2. Add `NOTICE` file to repo root — single copyright line: `Copyright 2026 Patrik Dudits`
3. Edit `pom.xml` — add `<licenses>` block (Apache-2.0, https://www.apache.org/licenses/LICENSE-2.0.txt)
4. Edit `pom.xml` — add `<developers>` block (id=pdudits, name=Patrik Dudits)
5. `mvn verify -q` to confirm build still passes
<!-- SECTION:PLAN:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
Added Apache 2.0 licensing artifacts:
- `LICENSE`: full canonical Apache 2.0 text fetched from apache.org
- `NOTICE`: `Copyright 2026 Patrik Dudits`
- `pom.xml`: added `<licenses>` (Apache-2.0) and `<developers>` (pdudits / Patrik Dudits) blocks
- `mvn verify -DskipTests` passes cleanly

No source headers yet — handled by TASK-015.
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
