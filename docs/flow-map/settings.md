# Flow map — area `settings`

Stored settings and Settings/Exceptions keys. Files covered: Settings.kt, SettingsNav.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/SettingsTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
| P-SET-01 | SettingsCodec | decode of an empty store gives the defaults (design values) | decode of an empty store gives the defaults (design values) | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-02 | SettingsCodec | encode then decode round-trips every field (v1 key names) | encode then decode round-trips every field (v1 key names) | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-03 | SettingsCodec | unknown names, wrong types and out-of-range dim fall back safely | unknown names, wrong types and out-of-range dim fall back safely | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-04 | SettingsCodec | summary and count labels | summary and count labels | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-05 | SettingsCodec | UP DOWN move between rows and clamp | UP DOWN move between rows and clamp | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-06 | SettingsCodec | toggle rows flip with OK, LEFT or RIGHT | toggle rows flip with OK, LEFT or RIGHT | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-07 | SettingsCodec | segmented rows step RTL | RIGHT earlier, LEFT later, OK cycles | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-08 | SettingsCodec | shape accent and tint rows step the same way | shape accent and tint rows step the same way | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-09 | SettingsCodec | dim bar | LEFT and OK raise by 5, RIGHT lowers, clamped 0 to 100 | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-10 | SettingsCodec | Exceptions row | OK or LEFT opens and loads apps, RIGHT does nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-11 | SettingsCodec | BACK on Settings finishes, other keys do nothing | BACK on Settings finishes, other keys do nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-12 | SettingsCodec | Exceptions | UP DOWN clamp to the list, OK toggles the focused app | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-13 | SettingsCodec | Exceptions | BACK or RIGHT returns - OK on an empty list does nothing | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-14 | SettingsCodec | step with an unknown current value starts from the first | step with an unknown current value starts from the first | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |
| P-SET-15 | SettingsCodec | toggles switch back on as well | toggles switch back on as well | SettingsTest.kt asserts state + effects | SettingsNav.kt `edit()`/`step()` (RTL direction) or SettingsCodec key names |

## Mode combinations

Every row × OK / LEFT / RIGHT, at the first, middle and last option (P-SET-06…10, 15). Exceptions screen × empty and non-empty list (P-SET-12, 13).

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
