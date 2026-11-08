# Flow map — area `gates`

Critical gate decision tables (one condition flipped per test). Files covered: Overlay.kt, TaskList.kt, Geometry.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/GatesTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|


| G-OK-scene | OK | only the overlay scene takes the tap-hold path | only the overlay scene takes the tap-hold path | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-btn | OK | other keys never enter the OK path | other keys never enter the OK path | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-busy | OK | a hold while switching opens no menu | a hold while switching opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-killing | OK | a hold during Kill All opens no menu | a hold during Kill All opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-OK-flying | OK | a hold during a fly-off opens no menu | a hold during a fly-off opens no menu | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-repeat | HOLD | the first press of OK opens nothing | the first press of OK opens nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-card | HOLD | a hold on Kill All opens nothing, on a pin it does | a hold on Kill All opens nothing, on a pin it does | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-busy | INPUT | no focus move while switching | no focus move while switching | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-killing | INPUT | no peek during Kill All | no peek during Kill All | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-INPUT-flying | INPUT | no peek during a fly-off | no peek during a fly-off | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
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
| G-READ-size | READ | a one-task binder list is not enough | a one-task binder list is not enough | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CLAMP-fits | CLAMP | a strip that fits is centred whole, whatever the focus | a strip that fits is centred whole, whatever the focus | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-CLAMP-ends | CLAMP | the long strip is pinned at both ends | the long strip is pinned at both ends | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |
| G-HOLD-release | HOLD | the release after a hold opens nothing | the release after a hold opens nothing | GatesTest.kt asserts state + effects | the gate condition named in the ID; `mutants.json` shows the flip |

## Mode combinations

One test per atomic condition; `docs/flow-map/mutants.json` removes each condition and a test must fail (`flowmap.py mutate`).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
