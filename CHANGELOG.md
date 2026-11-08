# Changelog

## 1.7 — 2026-11-09

- The row reads every task the system stores (`dumpsys activity recents` through Shizuku), not only the "visible" few:
  the TV hides tasks unused for 6 h from that list (2 shown while 47 were stored). Each stored app still reopens where it was.
- Usage access, read when the row opens (no background service; the system keeps ~10 days of events): apps used in the
  last 3 days that own no task fill the places left. The app grants itself usage access through Shizuku.
- Removal ledger: an app removed in the row (↑↑, Kill all) stays out until it is used again, so usage data can't bring it back.
- Shizuku off (after a TV reboot): the row is built from the last saved list + usage access and apps open from their start
  screen; removals are queued and done when Shizuku is back. The gate says "Allow Recent Apps in Shizuku" when only the grant is missing.
- Up to 20 apps; apps with a task always get a place before usage-only apps. Kill all never removes a home task.
- A removal queued while Shizuku was off also force-stops the app when it is replayed (unless the app was used again).
- The ⚙ button fades out while the "press again to remove" hint is up (they overlapped next to Kill all).
- Reopen on the A50 (Shizuku in shell mode): 0.18–0.33 s; with Shizuku off ~0.1 s.

## 1.6 — 2026-11-08
- Data methods checked against AOSP (SystemUI ActivityManagerWrapper, WM Shell RecentTasksController, Launcher3) and on the
  A50 with Shizuku in shell mode (as on the TV):
  - read: `getRecentTasks(max, RECENT_IGNORE_UNAVAILABLE, user)` first (system recents, most recent first); root tasks,
    `am stack list`, dumpsys stay as fallbacks. The old note "shell lacks REAL_GET_TASKS" was wrong.
  - switch: `startActivityFromRecents` first. Fixed: as shell, `moveTaskToFront(null package)` threw
    "package=null does not belong to uid=2000", so every switch on the TV fell back to a monkey relaunch; the fallback
    now passes `com.android.shell`.
  - Fixed: the system Settings app (FallbackHome, priority -1000) was taken for a launcher and never shown.
  - force-stop uses the current user (was user 0). removeTask + force-stop for dismiss / Kill all were already right.
- Tests: 150, 100 % lines and branches, 42 of 42 gate mutants caught.

## 1.5 — 2026-11-08
- The ⚙ Settings button moved above Kill all: ↑ on Kill all focuses it, OK opens Settings, ↓ returns, ← goes on to the
  apps. Without Kill all on screen it stays at the right end of the row.
- README brought up to date for 1.2–1.5 (controls, what's new, device screenshots).
- Tests: 143, 100 % lines and branches, 41 of 41 gate mutants caught.

## 1.4 — 2026-11-08
- Fixed (found on the TV): holding OK pinned the app at once and never showed the balloon menu. A real remote repeats
  a held OK every ~50 ms and a repeat ran the menu's first row. Now the hold is a 550 ms timer (design), repeats of OK
  are ignored, and the OK that opened the menu runs no row until it is released.
- New ⚙ Settings tile at the right end of the row — the Google TV remote has no Menu key, so 1.2/1.3 left Settings
  reachable only through the (broken) hold menu.
- Device tests now use a virtual remote (`tools/perf/rkey`, uinput) that holds keys like a real one; 1.3's
  `input keyevent --longpress` sent a single repeat and hid the bug.
- Tests: 137, 100 % lines and branches, 34 of 34 gate mutants caught.

## 1.3 — 2026-11-08
- Background image picker for TV (as Projectivy Launcher): the picker apps (Gallery, file managers, Files…) listed
  and asked directly — the Android TV "documents" stub is skipped — plus an in-app D-pad grid of the device's pictures
  (MediaStore, newest first; READ_MEDIA_IMAGES, granted on install with `-g`).
- The picture is prepared once: sub-sampled decode, centre crop to the screen shape, scaled to ≤ 1920×1080, JPEG in
  app storage; shown centre-cropped; decoded as a hardware bitmap only while the overlay shows.
- Tests: 130, 100 % lines and branches.

## 1.2 — 2026-11-08
- Design update "Recent Apps TV 2": pinned apps (group, badge, running dot, left/right, small/same size, list with Unpin
  all, empty-with-pins), select zoom on OK (tile → full screen in the app colour + name, then the app opens), balloon
  hold-OK menu (Pin/Unpin, Add to exceptions, App info, Settings) with toast, ↑ or ↓ twice to remove, centred header
  with counts, footer key hints, backgrounds Dim / Blur / Colour / Image, Exceptions Hide all / Show all, settings in
  four sections.
- Fixed: the name under the focused card blinked out and back on every left/right move (now a per-card cross-fade,
  whole-pixel motion); OK opened the app with no visible animation; an OK in the hold menu could also open the app on
  release; a release during an animation could eat the next OK.
- Tests: 122, 100 % lines and branches, 25 of 25 gate mutants caught.

## 1.1 — 2026-11-08
- Claude Design "Recent Apps TV" applied in full: RTL layout, focus ring + glow, name under the focused app, tinted
  Kill-all tile, settings/exceptions/Shizuku screens, motion spec (scrim fade, rise-in, focus glide, gap closing,
  fly-off with trail, staggered Kill all, fade-out on Back).
- Faster: hot reopen (the overlay steps aside instead of finishing), binder read of the task list (no process spawn),
  icons/labels prepared off the main thread and cached on disk, settings in SharedPreferences, R8.
- Zero cost while hidden: no service, timer or frame.
- Fixes: variable font rendered at weight 200; OK eaten after the hold menu; OK during a fly-off switched to the app
  being removed; volume keys swallowed while open; context menu width on small screens.
- Tests: logic moved to plain Kotlin (`logic/`), flow map with 96 tests, 100 % lines and branches, gates mutation-tested.

## 1.0 — 2026-05-31
- First release.
