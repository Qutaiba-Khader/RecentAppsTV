# Flow map — recentappstv

The stored, complete, deterministic set of this project's code paths and the checks each needs. **Update it with every code
change, run one full pass, fix, re-run until a pass is clean, stop.** Skill: `recentappstv-flowmap`.

```bash
python3 tools/flow/flowmap.py check     # the full pass. CLEAN = stop
python3 tools/flow/flowmap.py quick     # after a change: affected tests + map consistency
python3 tools/flow/flowmap.py update    # mechanical update: line numbers, skeleton rows, entries
```

<!-- TOTALS:START -->
**Last clean pass 2026-11-08 17:52:22 +0300:** 93 paths, 25 gate tests · lines 367/367 (100.0%) · branches 391/391 (100.0%) · 0 justified, 0 unjustified.
<!-- TOTALS:END -->

## Areas

| Area | Prefix | Map | Tests | Owns |
|---|---|---|---|---|
| overlay | P-OVL | [overlay.md](flow-map/overlay.md) | OverlayTest.kt | logic/Overlay.kt |
| tasks | P-TASK | [tasks.md](flow-map/tasks.md) | TaskListTest.kt | logic/TaskList.kt |
| settings | P-SET | [settings.md](flow-map/settings.md) | SettingsTest.kt | logic/Settings.kt, logic/SettingsNav.kt |
| geometry | P-GEO | [geometry.md](flow-map/geometry.md) | GeometryTest.kt | logic/Geometry.kt |
| gates | G-* | [gates.md](flow-map/gates.md) | GatesTest.kt | the conditions below |

Scope: the pure `logic/` package (level `full`: every line and branch tested, no ledger). The Android glue — Activities,
the Shizuku repository, composables — is driven through these functions and checked by the A50 device run (a separate,
approved step, `docs/TESTING.md`).

## Entry points

[flow-map/entries.md](flow-map/entries.md) lists every way work enters the code. A path not reachable from one of them is dead
code: remove it or justify it.

## Main flows

One chain per main flow, from an entry to its visible result. Name files and symbols, not line numbers, and mark each boundary:
`[NET]` network, `[DB]` database, `[BIN]` external program, `[DEV]` device. Each boundary has a fake in the tests.

```
open:    launcher/shortcut → RecentsActivity.onStart → reload → RecentTasksRepository.readRaw [DEV: Shizuku binder]
         → TaskList.read → TaskList.select → Overlay.loaded → RecentsOverlay (Geometry.stripOffset) → the row
key:     remote key → Keys.btn → Overlay.key → effects → RecentsActivity.run [DEV: moveTaskToFront/removeTask] → new state drawn
dismiss: UP,UP or DOWN,DOWN → Overlay.key (peek, flying) → AppCard fly-off → Overlay.flyComplete → Effect.Dismiss [DEV: force-stop + removeTask]
select:  OK → Overlay.key (pressing, selecting) → SelectZoom 500 ms + hold 400 ms → RecentsActivity.switchTo [DEV: moveTaskToFront / launch intent]
menu:    hold OK → Overlay.ctxKey → Effect.SetPin / AddException [DB] / AppInfo / OpenSettings
close:   BACK → Overlay.key → Effect.Close → fade 170 ms → moveTaskToBack (kept hot for the next open)
settings: SettingsActivity key → SettingsNav.key → SetEffect.Save → SettingsStore [DB: SharedPreferences] → next open reads it
```

## Critical gates

Branch coverage can pass while a sub-condition never decides anything. Each compound condition that decides what the user gets,
loses data or restarts work has a decision table: per atomic condition, a pair of inputs where only that condition changes and the
outcome changes. Tests are named `G-<GATE>-<condition>`; `flow-map/mutants.json` proves each one can fail.

| Gate | Where | Conditions | Outcome decided |
|---|---|---|---|
| G-OK | Overlay.key | scene = OVERLAY · btn = OK · !busy · !killing · flying = null | OK presses in / taps on release / holds open the menu, or the scene handles it on press |
| G-HOLD | Overlay.key | repeat = 1 · a card (task or pin) under focus · the release after a hold or a menu OK opens nothing | a held OK opens the menu; no stray app opens (A50 find, P-OVL-46) |
| G-INPUT | Overlay.key / overlayKey | busy · killing · flying · selecting | the overlay ignores keys while it is changing |
| G-DISMISS | Overlay.dismissStep | focused item is a running task · same direction twice | only tasks are swiped away, and only on the second press |
| G-ROW | OvState.items / Overlay.settle | pinned running app leaves the tasks · Kill All needs the setting and a task · Empty needs no tasks and no pins | who sits in the row |
| G-SELECT | TaskList.select | taskId > 0 · package not blank · first of its package · not excluded | which apps the user sees |
| G-READ | TaskList.read | binder result present · at least two tasks | which source the list is trusted from |
| G-CLAMP | Geometry.stripOffset | strip fits · clamp at both ends | Kill All never leaves the screen |

## The update rule

Every code change, with no exceptions:

1. Add or edit the rows in `flow-map/<area>.md` and their tests for each new or changed branch, mode or edge case. Removed code
   loses its rows. A changed compound condition updates its gate table.
2. `flowmap.py check`. New gaps get a test or a justified row.
3. Clean: commit map, tests and code together. A defect found: fix it, add its path, run the full pass again. Stop at the first
   clean pass. No random rounds in place of the pass.
