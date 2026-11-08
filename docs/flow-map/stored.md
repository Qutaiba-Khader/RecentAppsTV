# Flow map — area `stored`

1.7 row sources: the system's stored recents, usage access and the removal ledger. Files covered: Stored.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/StoredTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
| P-STO-01 | Stored.parse | the TV store (Android 14) | component, intent pkg and affinity fallbacks, flags, home, availability, idle time read; no id / no package skipped | StoredTest.kt asserts the result | Stored.kt — the regexes in `parse()` |
| P-STO-02 | Stored.parse | the A50 store (Android 11) | realActivity and the head `I=` fallback; no idle time gives -1 | StoredTest.kt asserts the result | Stored.kt — the regexes in `parse()` |
| P-STO-03 | Stored.parse | unreadable numbers | an id too big is skipped; huge flags / idle time fall back to 0 / -1 | StoredTest.kt asserts the result | Stored.kt — `parse()` number handling |
| P-STO-04 | Stored.candidates | stored tasks | store order kept, times from idle clamped to the task before, home / excluded / unavailable / id 0 dropped | StoredTest.kt asserts the result | Stored.kt — `candidates()` |
| P-STO-05 | Stored.candidates | visible tasks given | a visible task the store missed is added, a duplicate is not | StoredTest.kt asserts the result | Stored.kt — `candidates()` canary merge |
| P-STO-06 | Stored.merge | task and usage for one app | the task wins (resume) and takes the newer time; newest task per app | StoredTest.kt asserts the result | Stored.kt — `merge()` task branch |
| P-STO-07 | Stored.merge | usage-only apps with a ledger | shown unless used before its tombstone or the Kill all epoch | StoredTest.kt asserts the result | Stored.kt — `merge()` ledger check |
| P-STO-08 | Stored.merge | seed entries and repeated usage | a seed task beats usage, newer usage beats older, older ignored | StoredTest.kt asserts the result | Stored.kt — `merge()` soft branches |
| P-STO-09 | Stored.merge | exclusions and cap | excluded dropped, newest first, capped (default 20) | StoredTest.kt asserts the result | Stored.kt — `merge()` filter / take |
| P-STO-10 | Stored.foldUsage | saved map + new events | newest resume per app, pruned to the window | StoredTest.kt asserts the result | Stored.kt — `foldUsage()` |
| P-STO-12 | Stored.merge | usage-only apps newer than old tasks, small cap | every app with a task keeps its place; usage-only apps fill the rest | StoredTest.kt asserts the result | Stored.kt — `merge()` places |
| P-STO-13 | Stored.current | stored list / seen list | the first stored task not this app nor excluded (null when home); from a seen list the newest that isn't this app | StoredTest.kt asserts the result | Stored.kt — `current()` / `currentOf()` |
| P-STO-11 | Stored.remove / killAll / prune | removals | one tombstone; Kill all = epoch and no tombstones; prune drops stale and covered | StoredTest.kt asserts the result | Stored.kt — ledger helpers |

## Mode combinations

Shizuku on: live stored tasks (trusted) + usage (soft). Shizuku off: no tasks, seed + usage (both soft, ledger-filtered). The Android side
(`RecentTasksRepository.readStoredRow / resolveRow / replayPending`, `RowStore`) is checked by the A50 device run in shell mode.
