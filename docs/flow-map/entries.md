# Entry points

Every way work enters the code. Found by `flowmap.py update`; rows whose owner starts with `manual` are added by hand (timers, queues, UI events no pattern finds) and stay.
Owner = the area or test family that exercises it. A row with `TODO` fails the gate.

| Kind | Entry | File | Owner |
|---|---|---|---|
| intent | `RecentsActivity` launch (LEANBACK_LAUNCHER, shortcut `open_recents`, action `com.websnake.recentstv.OPEN_RECENTS`) → `onStart` → `reload()` → `Overlay.loaded` / `Overlay.needShizuku` | RecentsActivity.kt | manual · overlay (P-OVL-01…05); A50 device run |
| ui-event | remote key → `RecentsActivity.dispatchKeyEvent` → `Keys.btn` → `Overlay.key` | RecentsActivity.kt, Keys.kt | manual · overlay (P-OVL-06…38), gates G-OK/G-HOLD/G-INPUT |
| ui-event | card fly-off animation end → `onFlyComplete` → `Overlay.flyComplete` | RecentsActivity.kt | manual · overlay (P-OVL-10…12) |
| timer | Kill All: 520 ms after OK → `Overlay.killAllDone` | RecentsActivity.kt | manual · overlay (P-OVL-18) |
| system | Shizuku binder received / dead / permission result → `reload()` / `needShizuku` | RecentsActivity.kt | manual · overlay (P-OVL-05, 31); A50 device run (gate) |
| intent | `SettingsActivity` (gear, MENU, APPLICATION_PREFERENCES) → `dispatchKeyEvent` → `SettingsNav.key` | SettingsActivity.kt | manual · settings (P-SET-05…15) |
| storage | settings file read → `SettingsCodec.decode` (+ one-time v1.0 DataStore migration) | SettingsStore.kt | manual · settings (P-SET-01…03); A50 device run |
| system | task list read over Shizuku → `TaskList.read` / `fromRootTasks` / `select` | RecentTasksRepository.kt | manual · tasks (P-TASK-01…14), gates G-SELECT/G-READ |
| ui-event | layout of the row → `Geometry.layout` / `stripOffset`; settings scroll → `revealDelta` | RecentsOverlay.kt, Components.kt | manual · geometry (P-GEO-01…10), gates G-CLAMP |
