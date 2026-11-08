# Testing

## 1. The flow map (every change)

`python3 tools/flow/flowmap.py check --twice` — runs the 96 JVM tests with JaCoCo and fails on any untested line or
branch in `logic/`, any map row without a test, or a test without a row. `python3 tools/flow/flowmap.py mutate`
removes each critical condition in a copy and requires a failing test. See `docs/FLOW-MAP.md`.

On the owner's host the Gradle lane runs inside the build container (`tools/flow/jvm-lane.sh`); elsewhere point the
lane's `command` in `flowmap.json` at `./gradlew -q :app:testDebugUnitTest --rerun :app:createDebugUnitTestCoverageReport`.

## 2. Device run (before a release)

Release build on a real device by D-pad only (`adb shell input keyevent`): open, ← →, ↓ ↓ remove, ↑ gear,
hold OK menu (each option), Kill all → Empty, Settings (every row), Exceptions (toggle), Back, Shizuku stopped → gate
→ Shizuku started in **adb/shell mode** (the TV's identity) → list, remove and switch work.

1.1 was run on a Samsung Galaxy A50 (Android 11, 2340×1080, Shizuku 13.6 in root and in shell mode) on 2026-11-08.
Found and fixed on the way: font drawn at weight 200; OK eaten after the hold menu (also in 1.0); OK during a
fly-off switched to the app being removed; the context menu spanned the whole phone screen; full stops jumped to the
start of lines in the right-to-left settings.

1.2 was run on the same A50 on 2026-11-08 (Shizuku root mode): row with pins, pin / unpin from the hold menu, menu
fit on a short screen, select zoom (screen-recorded: press-in → grow → name → app), name cross-fade on ←/→ (recorded),
Kill all with a pin → "No recent apps" placeholder, Settings sections, Colour background, Image background picked with
the D-pad in the system picker, Exceptions bulk bar. Found and fixed: the menu was cut off at the top of a short screen;
an OK pressed in the hold menu opened the app under it on release (also in 1.1); a release during an animation kept the
hold flag and ate the next OK.

1.6 (same A50, 2026-11-08): an app_process probe run as shell (uid 2000) on the A50 and, read-only, on the TV showed
getRecentTasks works for shell (TV 6 = same apps as the root tasks, other order; A50 21 vs 3), moveTaskToFront with a
null package throws for shell, startActivityFromRecents works (2 = brought to front, 0 = relaunched a killed task). The
app then ran with Shizuku in shell mode: src=RECENTS (raw read 10–95 ms), OK → startActivityFromRecents=2 / =0, ↑ ↑ →
removeTask=true, Kill all → 12 × removeTask=true, Settings app listed and opened, hold menu and ⚙ still fine; root mode
re-checked (switch =2).

1.5 (same A50, 2026-11-08, `rkey`, on the released APK): → to Kill all, ↑ → Settings button focused, OK → Settings,
Back → list; ↑ ↓ → Kill all again, OK clears; ↑ ← → newest app, OK opens it; hold OK 1.5 s → balloon, nothing pinned;
pins only (no Kill all) → ⚙ at the right end of the row, → OK → Settings. README screenshots `settings-button.jpg` and
`menu-device.jpg` taken in this run.

1.4 (same A50, 2026-11-08): keys sent by `tools/perf/rkey`, a uinput virtual remote (root) that holds a key so
Android makes its own repeats, as the TV remote does. 1.3 reproduced the owner's report first: OK held 1.2 s pinned
Gallery with no menu. 1.4: OK held 1.2 s / 1.5 s / 0.9 s → balloon open, nothing pinned; menu rows Pin, Unpin, Add to
exceptions, App info, Settings each run only when chosen; held 450 ms = a tap → the app opens; hold → Back → OK opens
the app; ⚙ tile (→ → OK) opens Settings and Back returns to the list; ↑ ↑ removes; Kill all keeps the pin and shows
the placeholder.

1.3 (same A50): picture screen lists OneDrive, My Files, Gallery, Files; grid of the device pictures newest first;
a 6000×3375 photo picked from the grid → overlay with it in 126 ms, PSS back to ~61 MB 6 s after closing; a picture
picked through the Files app with the D-pad. On the TV (read-only check, nothing launched): OPEN_DOCUMENT resolves to
`com.android.tv.frameworkpackagestubs/.Stubs$DocumentsStub` first; real pickers there: Gallery, MiXplorer, Material
Files, DocumentsUI, the photo picker.

## 3. Speed

`tools/perf/measure.sh <label>` (cold and reopen times, D-pad frame stats, memory and CPU while hidden) and
`tools/perf/frames.sh <label>` (36 key presses at a person's pace). Both drive `adb -s <serial>`; edit the serial.

| A50, same scripts | 1.0 | 1.1 | 1.2 |
|---|---|---|---|
| reopen → apps shown (from the launch request) | 0.38–0.45 s | 0.28–0.36 s | 0.30–0.37 s |
| apps ready after the window starts (in-app log `RTV content …`) | — | 0.05–0.09 s | 0.09–0.26 s ¹ |
| cold start → apps shown | 0.78–0.83 s | 0.76–0.80 s | 0.52–0.61 s |
| slow frames, D-pad test | 53–60 % | 37–38 % | 42–55 % ² |
| median / p99 frame | 17–18 / 53–69 ms | 14 / 32–40 ms | 15–18 / 40–48 ms ² |
| GPU per frame (median) | 14 ms | 11 ms | 11 ms |
| hidden 120 s: CPU / wake-ups / frames | — | 10 ms / 0 / 0 | 70 ms (60 s) / 0 / 0 |
| memory held while hidden (PSS) | 64.7 MB | 39.6–44.8 MB | 39.6 MB |
| APK | 6.1 MB | 1.2 MB | 1.2 MB |

¹ also reads the pinned apps and each icon's colour. ² measured while the phone's load average stood at ~19 (memory
pressure from many open apps); the app's own UI thread was slow on 4–9 frames and its GPU time is unchanged.
