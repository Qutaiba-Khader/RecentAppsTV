# Flow map — recentappstv

The stored, complete, deterministic set of this project's code paths and the checks each needs. **Update it with every code
change, run one full pass, fix, re-run until a pass is clean, stop.** Skill: `recentappstv-flowmap`.

```bash
python3 tools/flow/flowmap.py check     # the full pass. CLEAN = stop
python3 tools/flow/flowmap.py quick     # after a change: affected tests + map consistency
python3 tools/flow/flowmap.py update    # mechanical update: line numbers, skeleton rows, entries
```

<!-- TOTALS:START -->
**Last clean pass 2026-11-09 03:01:24 +0300:** 125 paths, 59 gate tests · lines 509/509 (100.0%) · branches 593/593 (100.0%) · 0 justified, 0 unjustified.
<!-- TOTALS:END -->

## Areas

| Area | Prefix | Map | Tests | Owns |
|---|---|---|---|---|
| overlay | P-OVL | [overlay.md](flow-map/overlay.md) | OverlayTest.kt | logic/Overlay.kt |
| tasks | P-TASK | [tasks.md](flow-map/tasks.md) | TaskListTest.kt | logic/TaskList.kt |
| stored | P-STO | [stored.md](flow-map/stored.md) | StoredTest.kt | logic/Stored.kt |
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
| G-OK | Overlay.key / activate | scene = OVERLAY · btn = OK · !busy · !killing · flying = null · the ⚙ tile opens Settings | OK presses in / taps on release / holds open the menu, or the scene handles it on press |
| G-HOLD | Overlay.key / holdElapsed / ctx | key repeats never arm or open anything · the menu opens only from the HOLD_MS (550 ms) timer, while OK is still down (pressing), no menu open, not closing, nothing chosen · a card (task or pin) under focus · inside the menu a repeat, or the OK that opened it, runs no row · the release after a hold or a menu OK opens nothing | a held OK opens the balloon and keeps it open; no row runs by itself (TV find 1.3: the remote's ~50 ms repeats ran "Pin"); no stray app opens (A50 find, P-OVL-46) |
| G-INPUT | Overlay.key / overlayKey | busy · killing · flying · selecting | the overlay ignores keys while it is changing |
| G-DISMISS | Overlay.dismissStep | focused item is a running task · same direction twice | only tasks are swiped away, and only on the second press |
| G-GEAR | OvState.gearFocused / overlayKey / move | UP on Kill All · DOWN on the ⚙ · RIGHT on the ⚙ stays · counts only with the flag, in the overlay, with Kill All shown and focused | the ⚙ Settings button above Kill All is reached and left with the D-pad |
| G-ROW | OvState.items / Overlay.settle | the ⚙ is in the row (first) only when Kill All isn't shown · pinned running app leaves the tasks · Kill All needs the setting and a task · Empty needs no tasks and no pins | who sits in the row |
| G-SELECT | TaskList.select | taskId > 0 · package not blank · first of its package · not excluded | which apps the user sees |
| G-READ | TaskList.read | the system recents list (getRecentTasks) is non-empty · else: binder root tasks present · at least two tasks | which source the list is trusted from |
| G-CENTER | Geometry.stripOffset | short strip · either end of a long strip | the focused app is always in the middle (1.8) |
| G-ROW (1.7) | Stored.candidates / merge / parse | home · excluded-from-recents bit · unavailable · used before its removal (tombstone, Kill all epoch) · a task keeps its id over usage · usage-only apps only fill the places tasks leave | which stored / usage apps reach the row |
| G-TOGGLE | Overlay.relaunched | not closing · not opening an app | a second launch while shown closes the overlay |
| G-CURRENT | Overlay.activate / Stored.current | the focused app is the one under the overlay · home on top · excluded task on top | OK just closes the overlay (no zoom, no switch) |
| G-UPMENU | Overlay.overlayKey / ctxKey | UP on a task or pin (not the gear) · UP on a lowered card · DOWN on the bottom row · Close on a pin vs a task | UP opens the menu or puts a lowered card back; DOWN from Pin closes the menu; Close removes the app (a pin stays pinned) |

## The update rule

Every code change, with no exceptions:

1. Add or edit the rows in `flow-map/<area>.md` and their tests for each new or changed branch, mode or edge case. Removed code
   loses its rows. A changed compound condition updates its gate table.
2. `flowmap.py check`. New gaps get a test or a justified row.
3. Clean: commit map, tests and code together. A defect found: fix it, add its path, run the full pass again. Stop at the first
   clean pass. No random rounds in place of the pass.
