---
id: TASK-012
title: Emit Kubernetes Events on ScalingPolicy state transitions
status: To Do
assignee: []
created_date: '2026-03-12 14:41'
updated_date: '2026-03-16 15:21'
labels:
  - kubernetes
  - observability
milestone: m-0
dependencies: []
priority: low
ordinal: 4000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
When ScaledApplicationRegistry transitions a ScaledApplication to a new ServiceState, emit a Kubernetes Event resource on the corresponding ScalingPolicy CR (type Normal/Warning, reason e.g. "ScaledDown", "ScalingUp", "Ready"). This makes state history visible in `kubectl describe scalingpolicy`. Currently the only output is CRD status phase + logs.
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [ ] #1 Kubernetes Event is emitted on every ServiceState transition for the owning ScalingPolicy
- [ ] #2 Event reason matches the new state name (e.g. ScaledDown, ScalingUp, Running, Stopped)
- [ ] #3 Events are visible via kubectl describe scalingpolicy <name>
- [ ] #4 Event emission failure does not break the state transition
<!-- AC:END -->

## Definition of Done
<!-- DOD:BEGIN -->
- [ ] #1 All aceptance criteria covered
- [ ] #2 or rejected with explanation
- [ ] #3 Code is compiling and unit test verifies its relevant functionality
- [ ] #4 An integration test is written
- [ ] #5 or a task describing the test scenario is in the backlog
<!-- DOD:END -->
