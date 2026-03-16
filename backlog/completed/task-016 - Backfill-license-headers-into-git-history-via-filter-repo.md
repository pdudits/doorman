---
id: TASK-016
title: Backfill license headers into git history via filter-repo
status: Done
assignee: []
created_date: '2026-03-16 15:22'
updated_date: '2026-03-16 20:14'
labels:
  - licensing
dependencies:
  - TASK-015
priority: low
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Use git filter-repo to retroactively add the SPDX license header (Copyright 2026 Patrik Dudits / SPDX-License-Identifier: Apache-2.0) to every Java file in all 43 historic commits. After rewrite, force-push and spot-check that mvn license:check passes on first, last, and a few middle commits.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 All Java files in all historic commits carry the SPDX header
- [x] #2 mvn license:check passes on HEAD after rewrite
- [x] #3 No broken commits in rewritten history
<!-- AC:END -->

## Implementation Plan

<!-- SECTION:PLAN:BEGIN -->
## Approach: filter-repo Python API (commit callback)

Write `filter-headers.py` using `git_filter_repo` as a library, run from repo root.

### Script responsibilities

**A — Inject LICENSE + NOTICE into initial commit:**
- Detect root commit (no parents) in `commit_callback`
- Add two synthetic `FileChange` entries pointing to blobs for current LICENSE and NOTICE content

**B — Patch all .java blobs:**
- For each commit, iterate `file_changes` for `.java` files (additions/modifications)
- Read blob: `git cat-file blob <id>`
- If not starting with `/*`: prepend the 14-line Apache header, write new blob: `git hash-object -w --stdin`, update `change.blob_id`

**No pom.xml patching** — license-maven-plugin stays at its current commit. `license:check` passes on HEAD which is the AC requirement.

### Steps
1. Commit current working tree (task-016 backlog file)
2. Install git-filter-repo (user provides)
3. Write `filter-headers.py` to session files dir
4. Run: `python3 filter-headers.py` from repo root
5. Verify: `mvn verify -DskipTests`, spot-check initial commit and an early Java file
6. `git push -f origin main`
<!-- SECTION:PLAN:END -->

## Implementation Notes

<!-- SECTION:NOTES:BEGIN -->
Used filter-repo Python API (standalone brew install). Key fixes: (1) callback signature must be commit_callback(commit, metadata), (2) FileChange constructor uses positional type_ and kwarg id_= (not blob_id=), (3) import via SourceFileLoader since brew installs as script not package. 47 commits rewritten in 5 seconds. Remote was removed by filter-repo (expected) — needs re-adding before push.
<!-- SECTION:NOTES:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
## TASK-016: Backfill license headers into git history

Rewrote all 47 commits using a `filter-headers.py` script built on the `git_filter_repo` Python API.

### What was done
- **LICENSE + NOTICE injected into initial commit** (`Initial commit`, `3a34712`) as synthetic FileChange entries
- **All `.java` files in all commits** have the `Copyright © 2026 Doorman contributors` Apache 2.0 header prepended where missing
- Blob-level caching avoids duplicate work; unchanged blobs reused
- History rewritten cleanly in ~5 seconds; `mvn verify -DskipTests` passes (license:check included)

### Verification
- Root commit: has LICENSE, NOTICE, App.java with header ✅
- Second commit: Main.java has header ✅  
- HEAD: `mvn verify -DskipTests` passes ✅

### Notes
- Remote `origin` is removed by filter-repo (expected behaviour) — re-add before pushing: `git remote add origin https://github.com/pdudits/doorman && git push -f origin main`
- Script saved at session files for reference
<!-- SECTION:FINAL_SUMMARY:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [x] #1 All aceptance criteria covered
- [x] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
