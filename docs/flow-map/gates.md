# Flow map — area `gates`

Critical gate decision tables (one condition flipped per test). Files covered: Overlay.kt, TaskList.kt, Geometry.kt, Stored.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/GatesTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|


| G-OK-scene | OK | only the overlay scene takes the tap-hold path | only the overlay scene takes the tap-hold path | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-btn | OK | other keys never enter the OK path | other keys never enter the OK path | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-busy | OK | a hold while switching opens no menu | a hold while switching opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-killing | OK | a hold during Kill All opens no menu | a hold during Kill All opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-flying | OK | a hold during a fly-off opens no menu | a hold during a fly-off opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-repeat | HOLD | key repeats of a held OK never open the menu - only the timer does | key repeats of a held OK never open the menu - only the timer does | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-card | HOLD | a hold on Kill All or the gear opens nothing, on a task it does | a hold on Kill All or the gear opens nothing, on a task it does | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-busy | INPUT | no focus move while switching | no focus move while switching | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-killing | INPUT | no peek during Kill All | no peek during Kill All | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-flying | INPUT | no peek and no menu during a fly-off | no peek and no menu during a fly-off | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-selecting | INPUT | the select zoom swallows every key | the select zoom swallows every key | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-DISMISS-task | DISMISS | a pin never peeks, a task does | a pin never peeks, a task does | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-DISMISS-same | DISMISS | only the same direction twice flies the card | only the same direction twice flies the card | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-pinned | ROW | a pinned running app leaves the tasks | a pinned running app leaves the tasks | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-killall | ROW | Kill All needs the setting and at least one task | Kill All needs the setting and at least one task | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-empty | ROW | Empty only when there are neither tasks nor pins | Empty only when there are neither tasks nor pins | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-SELECT-id | SELECT | a task without a valid id is dropped | a task without a valid id is dropped | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-SELECT-blank | SELECT | a task without a package is dropped | a task without a package is dropped | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-SELECT-seen | SELECT | a package shows once | a package shows once | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-SELECT-excluded | SELECT | an excluded package is hidden | an excluded package is hidden | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-READ-null | READ | a failed binder read falls through | a failed binder read falls through | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-READ-recents | READ | a non-empty system recents list is used, an empty one is not | a non-empty system recents list is used, an empty one is not | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-READ-size | READ | a one-task binder list is not enough | a one-task binder list is not enough | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-removed | ROW | a usage entry from before its removal stays out, one after it comes back | a usage entry from before its removal stays out, one after it comes back | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-epoch | ROW | Kill all hides every usage entry from before it | Kill all hides every usage entry from before it | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-home | ROW | a home task never becomes a row candidate | a home task never becomes a row candidate | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-excluded | ROW | a task started excluded from recents never becomes a row candidate | a task started excluded from recents never becomes a row candidate | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-avail | ROW | an unavailable task never becomes a row candidate | an unavailable task never becomes a row candidate | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-resume | ROW | an app with a stored task keeps the task id when usage also has it | an app with a stored task keeps the task id when usage also has it | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-flag | ROW | only the exclude-from-recents bit marks a task excluded | only the exclude-from-recents bit marks a task excluded | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-room | ROW | usage-only apps only fill the places tasks leave | usage-only apps only fill the places tasks leave | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-TOGGLE-closing | TOGGLE | a second launch during the exit fade does nothing | a second launch during the exit fade does nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-TOGGLE-selecting | TOGGLE | a second launch while an app is opening does nothing | a second launch while an app is opening does nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-UPMENU-card | UPMENU | UP opens the menu on a task or pin, never on the in-row gear | UP opens the menu on a task or pin, never on the in-row gear | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-UPMENU-pin | UPMENU | Close on a pin keeps it pinned, on a task it flies off | Close on a pin keeps it pinned, on a task it flies off | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-UPMENU-peek | UPMENU | UP on a lowered card puts it back, on a normal card it opens the menu | UP on a lowered card puts it back, on a normal card it opens the menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-UPMENU-bottom | UPMENU | DOWN closes the menu only from the bottom row, higher rows step down | DOWN closes the menu only from the bottom row, higher rows step down | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CURRENT-match | CURRENT | only the app under the overlay closes instead of switching | only the app under the overlay closes instead of switching | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CURRENT-home | CURRENT | a launcher on top means no current app | a launcher on top means no current app | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CURRENT-excluded | CURRENT | an excluded overlay task on top is skipped | an excluded overlay task on top is skipped | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CENTER-short | CENTER | in a strip that fits, each focus moves the strip | the focused item is in the middle | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CENTER-ends | CENTER | the end items of a long strip | centred, not pinned | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-release | HOLD | the release after a hold opens nothing | the release after a hold opens nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-held | HOLD | a key repeat of OK in the menu runs no row, a new press does | a key repeat of OK in the menu runs no row, a new press does | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-same | HOLD | the OK that opened the menu runs no row until it is released | the OK that opened the menu runs no row until it is released | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-pressing | HOLD | the timer opens the menu only while OK is still down | the timer opens the menu only while OK is still down | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-open | HOLD | a timer while a menu is already open changes nothing | a timer while a menu is already open changes nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-gone | HOLD | a timer after the overlay started closing opens nothing | a timer after the overlay started closing opens nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-chosen | HOLD | a timer after an app was chosen opens nothing | a timer after an app was chosen opens nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-pin | HOLD | a hold on a pin opens its menu | a hold on a pin opens its menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-ROW-gear | ROW | the gear is in the row only when Kill All is not shown | the gear is in the row only when Kill All is not shown | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-gear | OK | OK on the gear opens Settings, on a task it opens the app | OK on the gear opens Settings, on a task it opens the app | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-up | GEAR | UP from Kill All focuses the gear, from a task it opens the menu | UP from Kill All focuses the gear, from a task it opens the menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-down | GEAR | DOWN from the gear returns to Kill All, from a task it peeks | DOWN from the gear returns to Kill All, from a task it peeks | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-right | GEAR | RIGHT on the gear stays, on a task it moves | RIGHT on the gear stays, on a task it moves | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-flag | GEAR | the gear is focused only after UP | the gear is focused only after UP | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-scene | GEAR | the gear counts only in the overlay scene | the gear counts only in the overlay scene | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-shown | GEAR | the gear above Kill All counts only while Kill All is shown | the gear above Kill All counts only while Kill All is shown | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-GEAR-focus | GEAR | the gear counts only while focus is on Kill All | the gear counts only while focus is on Kill All | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |

## Mode combinations

One test per atomic condition; `docs/flow-map/mutants.json` removes each condition and a test must fail (`flowmap.py mutate`).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
