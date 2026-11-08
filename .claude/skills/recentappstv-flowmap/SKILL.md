---
name: recentappstv-flowmap
description: The flow map of recentappstv: its stored, complete set of code paths with the checks for each, as deterministic tests plus a coverage gate. Use after ANY code change to recentappstv (update the map and tests with it), before a deploy or release, and when asked for a flow test or full trace of recentappstv. One clean pass, then stop.
---

# recentappstv flow map

Method: the base skill `flowmap`. This project's map: `docs/FLOW-MAP.md`, `docs/flow-map/*.md`; settings: `flowmap.json`.

```bash
python3 tools/flow/flowmap.py update   # mechanical: line numbers, skeleton rows, entries
python3 tools/flow/flowmap.py quick    # after a change: affected tests + map consistency
python3 tools/flow/flowmap.py check    # the full pass, before a deploy or release. CLEAN = stop
python3 tools/flow/flowmap.py mutate   # the gate tests can fail
```

Every code change updates the map in the same commit. Fill each `TODO` row the update adds. Fix what a pass finds, re-run the full
pass, stop at the first clean one. No random review rounds in place of it.

## Project notes

(What a future session must know here: fakes, how to run a lane, what must never be called by a test.)
