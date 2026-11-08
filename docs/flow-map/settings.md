# Flow map — area `settings`

Stored settings and Settings/Exceptions keys. Files covered: Settings.kt, SettingsNav.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/SettingsTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
|
| P-SET-01 | SettingsNav.key / SettingsCodec | decode of an empty store gives the defaults (design values) | decode of an empty store gives the defaults (design values) | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-02 | SettingsNav.key / SettingsCodec | encode then decode round-trips every field (v1 key names kept) | encode then decode round-trips every field (v1 key names kept) | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-03 | SettingsNav.key / SettingsCodec | unknown names, wrong types and out-of-range numbers fall back safely | unknown names, wrong types and out-of-range numbers fall back safely | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-04 | SettingsNav.key / SettingsCodec | the pin list keeps its order, drops blanks and repeats | the pin list keeps its order, drops blanks and repeats | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-05 | SettingsNav.key / SettingsCodec | summary and count labels | summary and count labels | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-06 | SettingsNav.key / SettingsCodec | rows follow the Background choice and carry their section titles | rows follow the Background choice and carry their section titles | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-07 | SettingsNav.key / SettingsCodec | UP DOWN move between rows and clamp | UP DOWN move between rows and clamp | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-08 | SettingsNav.key / SettingsCodec | toggle rows flip with OK, LEFT or RIGHT, both ways | toggle rows flip with OK, LEFT or RIGHT, both ways | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-09 | SettingsNav.key / SettingsCodec | segmented rows step RTL | RIGHT earlier, LEFT later, OK cycles, ends save nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-10 | SettingsNav.key / SettingsCodec | Pinned position and colour swatches read literally (LTR) | Pinned position and colour swatches read literally (LTR) | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-11 | SettingsNav.key / SettingsCodec | dim bar | LEFT and OK raise by 5, RIGHT lowers, clamped 0 to 100 | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-12 | SettingsNav.key / SettingsCodec | Exceptions and Pinned apps rows | OK or LEFT open a list and load apps, RIGHT nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-13 | SettingsNav.key / SettingsCodec | Background image row | OK or LEFT open the picture screen and load it, RIGHT nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-14 | SettingsNav.key / SettingsCodec | BACK on Settings finishes, other keys do nothing | BACK on Settings finishes, other keys do nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-15 | SettingsNav.key / SettingsCodec | list | UP reaches the bulk bar, DOWN clamps to the list, OK toggles the row | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-16 | SettingsNav.key / SettingsCodec | Pinned list | OK pins at the end, again unpins | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-17 | SettingsNav.key / SettingsCodec | bulk bar | LEFT RIGHT move between buttons, OK runs Hide all, Show all, Unpin all | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-18 | SettingsNav.key / SettingsCodec | list | BACK or RIGHT on a row return, OK past the list does nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-19 | SettingsNav.key / SettingsCodec | step with an unknown current value starts from the first | step with an unknown current value starts from the first | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-20 | SettingsNav.key / SettingsCodec | setPin from the overlay adds last or removes | setPin from the overlay adds last or removes | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-21 | SettingsNav.key / SettingsCodec | pictures | LEFT is the next picture (RTL), RIGHT the previous, both stop at the row ends | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-22 | SettingsNav.key / SettingsCodec | pictures | UP DOWN by a row, the first row goes up to the bar, a short last row clamps | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-23 | SettingsNav.key / SettingsCodec | pictures | OK uses the picture and returns to Settings | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-24 | SettingsNav.key / SettingsCodec | picker bar | LEFT RIGHT move between apps, OK opens the focused one | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |
| P-SET-25 | SettingsNav.key / SettingsCodec | pictures | with none loaded the focus is the bar - BACK returns, other keys nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `rows()`/`edit()`/`step()`/`listKey()`/`imagesKey()` or SettingsCodec key names |

## Mode combinations

Every row × OK / LEFT / RIGHT, at the first, middle and last option (P-SET-06…10, 15). Exceptions screen × empty and non-empty list (P-SET-12, 13).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
