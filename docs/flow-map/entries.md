# Entry points

Every way work enters the code. Found by `flowmap.py update`; rows whose owner starts with `manual` are added by hand (timers, queues, UI events no pattern finds) and stay.
Owner = the area or test family that exercises it. A row with `TODO` fails the gate.

| Kind | Entry | File | Owner |
|---|---|---|---|
| intent | `RecentsActivity` launch (LEANBACK_LAUNCHER, shortcut `open_recents`, action `com.websnake.recentstv.OPEN_RECENTS`) → `onStart` → `reload()` → `Overlay.loaded` / `Overlay.needShizuku` | RecentsActivity.kt | manual · overlay (P-OVL-01…05); A50 device run |
| ui-event | remote key → `RecentsActivity.dispatchKeyEvent` → `Keys.btn` → `Overlay.key` | RecentsActivity.kt, Keys.kt | manual · overlay (P-OVL-10…46), gates G-OK/G-HOLD/G-INPUT/G-DISMISS |
| ui-event | card fly-off animation end → `onFlyComplete` → `Overlay.flyComplete` | RecentsActivity.kt | manual · overlay (P-OVL-16…18, 35) |
| timer | Kill All: 520 ms after OK → `Overlay.killAllDone` | RecentsActivity.kt | manual · overlay (P-OVL-24, 25) |
| system | Shizuku binder received / dead / permission result → `reload()` / `needShizuku` | RecentsActivity.kt | manual · overlay (P-OVL-05, 43); A50 device run (gate) |
| intent | `SettingsActivity` (menu row Settings, MENU, Empty OK, APPLICATION_PREFERENCES) → `dispatchKeyEvent` → `SettingsNav.key` | SettingsActivity.kt | manual · settings (P-SET-06…25) |
| storage | settings file read → `SettingsCodec.decode` (+ one-time v1.0 DataStore migration) | SettingsStore.kt | manual · settings (P-SET-01…04); A50 device run |
| system | task list read over Shizuku → `TaskList.read` / `fromRootTasks` / `select` | RecentTasksRepository.kt | manual · tasks (P-TASK-01…14), gates G-SELECT/G-READ |
| ui-event | layout of the row → `Geometry.layout` / `stripOffset`; settings scroll → `revealDelta` | RecentsOverlay.kt, Components.kt | manual · geometry (P-GEO-01…14), gates G-CLAMP |
| ui-event | select zoom end (900 ms after OK) → `RecentsActivity.switchTo` → moveTaskToFront / launch intent | RecentsActivity.kt | manual · overlay (P-OVL-21, 22); A50 device run |
| system | picture screen: grid pick or picker-app result → `BackdropImage.import` (`Geometry.sampleSize` / `centerCrop`) → `SettingsStore.save` | SettingsActivity.kt, ImageSources.kt, BackdropImage.kt | manual · settings (P-SET-13, 21…25), geometry (P-GEO-13, 14); A50 device run (grid + Files app, D-pad) |
