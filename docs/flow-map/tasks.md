# Flow map — area `tasks`

Reading and filtering the task list. Files covered: TaskList.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/TaskListTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
| P-TASK-01 | TaskList.read | am stack list parses every task in z-order | am stack list parses every task in z-order | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-02 | TaskList.read | dumpsys recents | affinity, realActivity and cmp fallbacks, skips unreadable | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-03 | TaskList.read | dumpsys block with a package but no id keeps -1 (filtered later) | dumpsys block with a package but no id keeps -1 (filtered later) | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-04 | TaskList.read | binder root tasks flatten in order | null and blank entries skipped | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-05 | TaskList.read | read prefers the binder when it has two or more tasks | read prefers the binder when it has two or more tasks | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-06 | TaskList.read | read falls to am stack list when the binder fails or is short | read falls to am stack list when the binder fails or is short | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-07 | TaskList.read | read falls to dumpsys when both are short | read falls to dumpsys when both are short | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-08 | TaskList.read | nothing anywhere | the best short list, or NONE | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-09 | TaskList.read | select keeps the front-most task per package, drops invalid and excluded, caps | select keeps the front-most task per package, drops invalid and excluded, caps | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-10 | TaskList.read | select caps at twelve by default | select caps at twelve by default | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-11 | TaskList.read | idsFor returns every task of the package, else the fallback | idsFor returns every task of the package, else the fallback | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-12 | TaskList.read | home detection by resolved launchers and known launcher prefixes | home detection by resolved launchers and known launcher prefixes | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-13 | TaskList.read | dumpsys | an id too large for Int becomes -1 | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |
| P-TASK-14 | TaskList.read | all short | the longer list wins, binder on a tie, empty binder loses to stack | TaskListTest.kt asserts state + effects | TaskList.kt — the parser regex or the source order in `read()` |

## Mode combinations

Source order binder → `am stack list` → dumpsys, each with full / short / failed results (P-TASK-05…08, 14). Android 10–11 StackInfo and 12+ RootTaskInfo field names are both fed through `fromRootTasks` by the repository.

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
