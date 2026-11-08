# Flow map — area `geometry`

Carousel positions, clamp, keep-in-view, icon size. Files covered: Geometry.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/GeometryTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
|
| P-GEO-01 | Geometry | layout places items with gap and end padding | layout places items with gap and end padding | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-02 | Geometry | empty layout is just padding | out-of-range centre is the middle | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-03 | Geometry | a strip that fits is centred as a whole | a strip that fits is centred as a whole | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-04 | Geometry | a long strip centres the focused item | a long strip centres the focused item | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-05 | Geometry | clamp | Kill All on the right never leaves the screen, nor the oldest on the left | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-06 | Geometry | no viewport yet means no offset | no viewport yet means no offset | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-07 | Geometry | revealDelta | visible needs nothing, above scrolls up, below scrolls down | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-08 | Geometry | revealDelta | a row taller than the window aligns its top | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-09 | Geometry | icon raster size covers the largest drawn size, bounded | icon raster size covers the largest drawn size, bounded | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-10 | Geometry | a negative index also falls back to the middle | a negative index also falls back to the middle | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-11 | Geometry | per-gap layout | the pinned group packs tighter inside, wider at its edges | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-12 | Geometry | a gap list shorter than the items counts the missing gaps as 0 | a gap list shorter than the items counts the missing gaps as 0 | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-13 | Geometry | sample size | a huge photo is decoded small but never below the screen | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |
| P-GEO-14 | Geometry | centre crop | wide trims the sides, tall trims top and bottom, both scaled to the screen | GeometryTest.kt asserts state + effects | Geometry.kt arithmetic (px) |

## Mode combinations

Strip shorter / longer than the viewport × focus at either end and in the middle (P-GEO-03…05).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
