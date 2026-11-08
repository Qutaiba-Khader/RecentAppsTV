# Changelog

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
