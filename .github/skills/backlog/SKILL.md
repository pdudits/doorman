---
name: backlog
description: Consult at task boundaries and during planning. Invoke BEFORE starting a task (set status In Progress, review ACs), AFTER completing a task (check ACs/DoD, write final summary), and when creating new tasks. Also invoke when the user asks about what to work on next.
---

# Backlog Workflow Guide

The project uses Backlog.md (via MCP tools) as the authoritative source of task state.
Always reflect work in the backlog — not only in SQL todos or session memory.

## At task start

1. Call `backlog-task_view` to read the full task (ACs, plan, notes).
2. Call `backlog-get_task_execution_guide` to get current workflow rules.
3. Set the task status to **In Progress** via `backlog-task_edit`.
4. If the task has no implementation plan yet, add one with `planSet`.

## During implementation

- Append implementation notes as you discover things: `backlog-task_edit` with `notesAppend`.
- If you hit a significant decision point or bug, record it immediately — don't wait until done.
- Keep SQL todos in sync if you use them for sub-step tracking.

## At task completion

1. Call `backlog-get_task_finalization_guide` for the finalization checklist.
2. Check off each acceptance criterion: `acceptanceCriteriaCheck`.
3. Check off each Definition of Done item: `definitionOfDoneCheck`.
4. Write a final summary (PR-style): `backlog-task_edit` with `finalSummary`.
5. Mark the task **Done**: `backlog-task_edit` with `status: "Done"`.
6. Call `backlog-task_complete` to move it to the completed folder.

## When creating new tasks

1. Call `backlog-get_task_creation_guide` before creating tasks.
2. Use `backlog-task_create` with title, description, acceptance criteria, and labels.
3. Add dependencies via `dependencies` field when tasks must be sequenced.
4. Break large tasks into subtasks with `parentTaskId`.

## When deciding what to work on next

1. Call `backlog-task_list` with `status: "To Do"` to see the queue.
2. Check dependencies and priority before picking the next task.
3. Prefer tasks whose dependencies are all **Done**.
