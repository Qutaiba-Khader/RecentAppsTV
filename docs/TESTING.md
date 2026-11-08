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

## 3. Speed

`tools/perf/measure.sh <label>` (cold and reopen times, D-pad frame stats, memory and CPU while hidden) and
`tools/perf/frames.sh <label>` (36 key presses at a person's pace). Both drive `adb -s <serial>`; edit the serial.

| A50, same scripts | 1.0 | 1.1 |
|---|---|---|
| reopen → apps shown (from the launch request) | 0.38–0.45 s | 0.28–0.36 s |
| apps ready after the window starts (in-app log `RTV content …`) | — | 0.05–0.09 s |
| cold start → apps shown | 0.78–0.83 s | 0.76–0.80 s |
| slow frames, D-pad test | 53–60 % | 37–38 % |
| median / p99 frame | 17–18 / 53–69 ms | 14 / 32–40 ms |
| GPU per frame (median) | 14 ms | 11 ms |
| hidden 120 s: CPU / wake-ups / frames | — | 10 ms / 0 / 0 |
| memory held while hidden (PSS) | 64.7 MB | 39.6–44.8 MB |
| APK | 6.1 MB | 1.2 MB |
