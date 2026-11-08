# Flow map — area `geometry`

Carousel positions, clamp, keep-in-view, icon size. Files covered: Geometry.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/GeometryTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
| P-GEO-01 | Geometry.layout | layout places items with gap and end padding | layout places items with gap and end padding | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-02 | Geometry.layout | empty layout is just padding | out-of-range centre is the middle | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-03 | Geometry.layout | a strip that fits is centred as a whole | a strip that fits is centred as a whole | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-04 | Geometry.layout | a long strip centres the focused item | a long strip centres the focused item | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-05 | Geometry.layout | clamp | Kill All on the right never leaves the screen, nor the oldest on the left | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-06 | Geometry.layout | no viewport yet means no offset | no viewport yet means no offset | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-07 | Geometry.layout | revealDelta | visible needs nothing, above scrolls up, below scrolls down | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-08 | Geometry.layout | revealDelta | a row taller than the window aligns its top | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-09 | Geometry.layout | icon raster size covers the largest drawn size, bounded | icon raster size covers the largest drawn size, bounded | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |
| P-GEO-10 | Geometry.layout | a negative index also falls back to the middle | a negative index also falls back to the middle | GeometryTest.kt asserts state + effects | Geometry.kt — the arithmetic; then the dp values in RecentsOverlay |

## Mode combinations

Strip shorter / longer than the viewport × focus at either end and in the middle (P-GEO-03…05).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
