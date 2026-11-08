# Flow map — area `overlay`

Overlay focus engine and keys. Files covered: Overlay.kt (in `app/src/main/java/com/websnake/recentstv/logic/`). Tests: `app/src/test/java/com/websnake/recentstv/logic/OverlayTest.kt`.
Run: `python3 tools/flow/flowmap.py check` (the JVM lane runs inside CT 200 via `tools/flow/jvm-lane.sh`).

## Paths

| ID | Entry | Conditions | Expected | Checks | If it fails |
|---|---|---|---|---|---|
| P-OVL-01 | Overlay.key | load with tasks and Kill All focuses the newest task | load with tasks and Kill All focuses the newest task | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-02 | Overlay.key | load without Kill All focuses index 0 = newest | load without Kill All focuses index 0 = newest | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-03 | Overlay.key | load with no tasks shows Empty and hides Kill All | load with no tasks shows Empty and hides Kill All | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-04 | Overlay.key | a reload clears every transient guard | a reload clears every transient guard | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-05 | Overlay.key | Shizuku missing routes to the gate, focus on the primary button | Shizuku missing routes to the gate, focus on the primary button | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-06 | Overlay.key | RIGHT moves toward Kill All and stops there (RTL) | RIGHT moves toward Kill All and stops there (RTL) | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-07 | Overlay.key | LEFT moves toward older apps and stops at the last | LEFT moves toward older apps and stops at the last | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-08 | Overlay.key | moving focus cancels a pending peek | moving focus cancels a pending peek | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-09 | Overlay.key | DOWN twice peeks then flies the card off | nothing is removed until it lands | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-10 | Overlay.key | fly-off landing removes the task, keeps focus on a neighbour, then dismisses it | fly-off landing removes the task, keeps focus on a neighbour, then dismisses it | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-11 | Overlay.key | removing the last task leaves the Empty scene | removing the last task leaves the Empty scene | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-12 | Overlay.key | a fly-off for an unknown task only clears the flag | a fly-off for an unknown task only clears the flag | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-13 | Overlay.key | DOWN on Kill All does nothing | DOWN on Kill All does nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-14 | Overlay.key | while a card is flying, DOWN and L-R are ignored | while a card is flying, DOWN and L-R are ignored | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-15 | Overlay.key | UP cancels a peek, otherwise focuses the gear | UP cancels a peek, otherwise focuses the gear | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-16 | Overlay.key | gear focused | DOWN LEFT RIGHT leave it, OK opens settings | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-17 | Overlay.key | OK tap on a card switches to it on release and blocks further input | OK tap on a card switches to it on release and blocks further input | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-18 | Overlay.key | OK on Kill All clears everything, then the Empty scene | OK on Kill All clears everything, then the Empty scene | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-19 | Overlay.key | holding OK opens the context menu | the release does not switch | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-20 | Overlay.key | after the menu closes, the next OK tap works (stale long-press flag) | after the menu closes, the next OK tap works (stale long-press flag) | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-21 | Overlay.key | holding OK on Kill All never opens a menu | holding OK on Kill All never opens a menu | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-22 | Overlay.key | context menu | UP DOWN clamp, LEFT closes | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-23 | Overlay.key | context menu | add to exceptions hides the app at once | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-24 | Overlay.key | context menu | kill app removes it and kills it | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-25 | Overlay.key | context menu | app info opens settings and closes the overlay | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-26 | Overlay.key | a menu whose task vanished just closes | a menu whose task vanished just closes | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-27 | Overlay.key | BACK closes the menu first, then the overlay | closing ignores keys | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-28 | Overlay.key | MENU opens settings from any scene | MENU opens settings from any scene | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-29 | Overlay.key | Empty | OK or UP opens settings, other keys nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-30 | Overlay.key | Loading ignores keys | key releases do nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-31 | Overlay.key | Shizuku gate | LEFT to Retry, RIGHT back, OK acts from either | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-32 | Overlay.key | busy or killing overlay ignores navigation and OK | busy or killing overlay ignores navigation and OK | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-33 | Overlay.key | OK and other keys with a focus past the end do nothing | OK and other keys with a focus past the end do nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-34 | Overlay.key | focusedTask is null outside the overlay scene | ctxTask null without a menu | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-35 | Overlay.key | OK release with the menu open but no hold changes nothing | OK release with the menu open but no hold changes nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-36 | Overlay.key | OK while a card flies or Kill All runs is ignored | OK while a card flies or Kill All runs is ignored | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-37 | Overlay.key | a hold on a focus past the end, or a longer hold, opens no menu | a hold on a focus past the end, or a longer hold, opens no menu | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |
| P-OVL-38 | Overlay.key | BACK while still holding OK closes the menu | the release then does nothing | OverlayTest.kt asserts state + effects | Overlay.kt — the `key()` branch for that scene/button |

## Mode combinations

Scene (LOADING, OVERLAY, EMPTY, SHIZUKU) × Kill All shown/hidden × transient guards (gear, peek, flying, killing, busy, context menu, closing). Each guard is crossed with every key in P-OVL-14/16/22/27/30/32/36 and the G-OK / G-INPUT gates.

## Not exercised — justified

| Location | Anchor | Class | Reason |
|---|---|---|---|
