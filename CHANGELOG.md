# Changelog

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
