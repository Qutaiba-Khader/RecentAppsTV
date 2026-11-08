# Handoff: Recent Apps for Android TV (Shizuku-powered)

> **Read this first.** The files in `design_reference/` are an **HTML design prototype** — they
> demonstrate the intended look, layout, focus behavior, and interaction flows at 1920×1080. They are
> **not** production code to port line-for-line. Your job is to **build a native Android TV app (Kotlin)**
> that reproduces these designs and behaviors using real Android primitives (Activities, Views/Compose,
> Shizuku, ActivityManager/UsageStats). Where the prototype fakes something (icons, the "running app"
> backdrop, app switching), wire it to the real system.

---

## 1. What we're building

Many Android TV / Google TV devices **have no system "Recent Apps" (overview) UI** — there's no
multitasking switcher the way phones have. This app fills that gap: a lightweight **Recent Apps overlay**
that appears as a **dialog-style window on top of whatever app is currently running**, lets the user
**switch tasks**, **dismiss a single task** (D-pad swipe-to-dismiss), or **kill all**, using **remote /
D-pad navigation only** (no touch).

Because Android TV restricts the APIs needed to read the task list and to remove tasks, the app uses
**Shizuku** to obtain the elevated permissions at runtime.

The overlay is **launched externally** — we do **not** build any "how to open" gesture/trigger setting.
Instead we expose:
- a **public Activity** that shows the overlay (themed as a dialog), and
- an **`android.intent.action.MAIN` / launcher shortcut** (static `<shortcut>` + dynamic shortcut) so
  launchers, button-remapper apps, or other apps that surface shortcuts can invoke it.

### Platform targets
- **Form factor:** Android TV / Google TV (leanback). Also acceptable on AOSP TV boxes.
- **minSdk:** 23 (Shizuku supports 23+); **targetSdk:** latest stable. Confirm against the Shizuku API version you pin.
- **Language:** Kotlin. UI in **either** Android Views (leanback-friendly) **or** Jetpack Compose for TV — your call; pick one and be consistent. (Notes below assume Views/Compose-agnostic terms like "focusable", "D-pad".)
- **Orientation:** landscape, fixed.
- **Input:** D-pad / remote ONLY. Every interactive element must be `focusable` and reachable via DPAD. No reliance on touch or hover.

---

## 2. High-level architecture

```
app/
 ├─ RecentsActivity            // dialog-themed, translucent; hosts the overlay UI
 ├─ SettingsActivity           // leanback settings (Show Kill All / Size / Shape / Exceptions)
 ├─ ShizukuGateActivity (opt)  // or an in-overlay state; shows "Shizuku not running"
 ├─ data/
 │   ├─ RecentTasksRepository  // reads task list via Shizuku-backed ActivityManager/IActivityTaskManager
 │   ├─ PackageRepository      // installed launchable apps + icons + labels (PackageManager)
 │   └─ SettingsStore          // DataStore: showKillAll, size, shape, exceptions set
 ├─ shizuku/
 │   └─ ShizukuManager         // binder lifecycle, permission request, privileged calls
 └─ ui/                        // RecentsView/carousel, cards, kill-all tile, focus engine
```

### Shizuku integration (the core enabler)
- Add the **Shizuku API** dependency (`dev.rikka.shizuku:api` + `:provider`) and the Sui/Shizuku provider in the manifest per the official docs. Pin a specific version.
- On launch of `RecentsActivity`:
  1. Check `Shizuku.pingBinder()`. If the binder is **not** alive → show the **"Shizuku not running"** state (Surface 3).
  2. If alive but permission not granted → call `Shizuku.requestPermission(...)`; handle the result callback. If denied → keep the gate state with a "Grant" button.
  3. If granted → proceed to read tasks and render the overlay.
- **Reading recent tasks:** use Shizuku to call the hidden `ActivityManager` / `IActivityTaskManager.getRecentTasks(...)` (or `getTasks`) as a privileged caller. Map each `RecentTaskInfo` → `{ packageName, taskId, label, icon, baseIntent }`. Filter out our own package, the home/launcher, and anything in the **Exceptions** set.
- **Switching to a task:** `IActivityTaskManager.moveTaskToFront(taskId, ...)` (privileged) — or relaunch via `baseIntent` if moving isn't available.
- **Removing a single task (dismiss):** `ActivityManager.removeTask(taskId)` via Shizuku, or `finishAndRemoveTask`-equivalent through the privileged binder.
- **Kill all:** iterate the visible task list and remove each; optionally `forceStopPackage` for excepted-free behavior if you have the permission — but prefer `removeTask` per task to match the "clear recents" mental model.
- Abstract ALL of the above behind `RecentTasksRepository` so the UI never touches Shizuku directly, and so it can be stubbed for previews/tests.

> If a privileged call isn't available on a given device/ROM, degrade gracefully: show the task from
> `UsageStatsManager` where possible and disable the actions you can't perform, rather than crashing.

---

## 3. Launch surface: dialog window + shortcut

### RecentsActivity must look/behave like a dialog over the running app
- **Theme:** translucent, floating — NOT a full opaque screen. Use a dialog/translucent theme:
  ```xml
  <style name="Theme.Recents.Overlay" parent="Theme.Leanback">
      <item name="android:windowIsTranslucent">true</item>
      <item name="android:windowBackground">@android:color/transparent</item>
      <item name="android:windowContentOverlay">@null</item>
      <item name="android:windowAnimationStyle">@style/Animation.Recents</item>
      <item name="android:windowNoTitle">true</item>
      <item name="android:backgroundDimEnabled">true</item>
      <item name="android:backgroundDimAmount">0.66</item> <!-- the 60–70% scrim -->
  </style>
  ```
  The activity's own root view draws the carousel; the **system dim** provides the scrim so the live
  app remains faintly visible behind it (matching the prototype's `#scrim` at ~66% black). Add a subtle
  blur (`Window.setBackgroundBlurRadius` / `RenderEffect`) on API levels that support it — optional.
- `RecentsActivity` should be `singleInstance` (or `singleTask`), `excludeFromRecents="true"`,
  `taskAffinity=""`, `launchMode="singleInstance"`, `resizeableActivity="false"`,
  `android:exported="true"` (so external launchers can start it).
- **Entry animation:** scrim fades in (~320ms), strip eases up into place. **BACK** finishes the activity
  (overlay closes, underlying app resumes).

### Manifest — make it launchable + shortcut-able
```xml
<activity
    android:name=".RecentsActivity"
    android:theme="@style/Theme.Recents.Overlay"
    android:exported="true"
    android:launchMode="singleInstance"
    android:excludeFromRecents="true"
    android:taskAffinity=""
    android:screenOrientation="landscape">

    <!-- Appears in the TV launcher AND as an app target; also makes it a valid shortcut intent -->
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
        <category android:name="android.intent.category.DEFAULT" />
    </intent-filter>

    <!-- Static launcher shortcut so shortcut-surfacing apps / remappers can trigger it -->
    <meta-data
        android:name="android.app.shortcuts"
        android:resource="@xml/shortcuts" />
</activity>
```

`res/xml/shortcuts.xml`:
```xml
<shortcuts xmlns:android="http://schemas.android.com/apk/res/android">
    <shortcut
        android:shortcutId="open_recents"
        android:enabled="true"
        android:icon="@drawable/ic_recents_shortcut"
        android:shortcutShortLabel="@string/shortcut_recents_short"
        android:shortcutLongLabel="@string/shortcut_recents_long">
        <intent
            android:action="android.intent.action.VIEW"
            android:targetPackage="<your.package>"
            android:targetClass="<your.package>.RecentsActivity" />
        <categories android:name="android.shortcut.conversation" />
    </shortcut>
</shortcuts>
```
Also publish a **dynamic shortcut** at runtime (`ShortcutManager.addDynamicShortcuts`) with the same
intent so apps that read dynamic shortcuts can find it. Document the exact component name
(`<your.package>/.RecentsActivity`) in the README/store listing so power users can bind a remote button
to it via remapper apps. **Do not build any in-app trigger/gesture setting.**

---

## 4. Surfaces (recreate these — see `design_reference/`)

> Fidelity: **High.** Colors, type, spacing, and motion below are final. Reproduce them. The HTML uses
> Manrope; on-device use a comparable clean geometric sans (Manrope is open-source and shippable, or use
> the platform default if you prefer — keep weights 500/700/800 for hierarchy).

### Global design language
- **Direction:** **Full RTL.** Root layout direction = RTL. The carousel grows **right→left**: the
  **Kill All** tile is pinned at the **far right (start)**, the **most-recent task (#1)** sits immediately
  to its left, then #2, #3 … older tasks continue leftward and scroll off the left edge.
  - Keep **English text runs LTR** inside the RTL layout (labels, counts, descriptions) so digits and
    punctuation don't reorder — in the prototype these nodes carry `direction: ltr`. In Android, set
    `android:textDirection="ltr"` (or `firstStrong`) on English `TextView`s while the container stays RTL.
- **One accent color:** Android green **`#3DDC84`** — used ONLY for focus rings and the Kill All tile.
  Nothing else is green.
- **10-foot legibility:** minimum on-screen text equivalent to **~26px @1080p**; focus state must be the
  single strongest visual cue on every screen.

### Design tokens (from `tv/styles.css`)
| Token | Value |
|---|---|
| bg (solid screens) | `#0a0b0d` |
| running-app base | `#101317` |
| scrim | `rgba(0,0,0,0.66)` (system dim 0.66) |
| surface | `#15171b` |
| surface-2 | `#1d2026` |
| card bg (neutral) | `#23262d` |
| hairline | `rgba(255,255,255,0.08)` |
| text | `#f3f5f7` |
| text-dim | `#9aa0a8` |
| text-faint | `#5e646c` |
| **accent** | **`#3DDC84`** |
| accent (pressed) | `#2fc973` |
| accent tint | `rgba(61,220,132,0.14)` |
| accent glow | `rgba(61,220,132,0.55)` |
| focus ring | 5px solid accent + 46px/6px accent glow shadow |

**Type scale @1080p** (px): hero 62 / title 46 / row 40 / label 34 / body 30 / small 26 / mono 22.
Weights: 800 (titles), 700 (labels/rows), 500 (descriptions).

**Card sizing** (driven by Settings → Size): Small **142**, Medium **180**, Large **230** px square.
**Card corner radius** (Settings → Shape): Square **16**, Rounded **32**, Circle **50%**.

---

### Surface 1 — RECENT APPS OVERLAY (primary)
**File:** `design_reference/Recent Apps TV.html` (scene "Overlay"). Logic in `tv/app.js`.

- Translucent dialog over the running app. Scrim ~66% black (system dim), faint blur.
- A **single horizontal row** of **icon-only** app cards, **vertically centered**. No thumbnails/snapshots — **app icon only** (use the real launcher icon from `PackageManager.getApplicationIcon`).
- **Header (top, centered):** "Recent apps" title with the count line centered under it (e.g. "7 apps · 3 pinned", LTR text).
- **Card:** rounded container holding the app icon at `--card-size`. Drop shadow `0 18px 40px -16px rgba(0,0,0,.7)`.
- **Focused card (the critical cue):**
  - scales to **1.15×**,
  - **glowing accent ring**: `0 0 0 5px #3DDC84` + `0 0 46px 6px rgba(61,220,132,.55)`,
  - reveals the **app NAME label** beneath it (700, ~34px) plus a small category/subtitle line (dim).
  - Unfocused cards: ~0.9× scale, ~80% opacity, **no label**.
- **Carousel behavior:** focused item centers; the strip **clamps** at both ends so the Kill All tile is never pushed off-screen (see `centerFocused()` in `tv/app.js`). Smooth ease (~340ms, `cubic-bezier(.22,.61,.36,1)`).
- **Kill All tile (far right / start):** visually distinct and **larger** than the cards
  (≈1.34× width, 1.18× height of a card), **accent-tinted** background `rgba(61,220,132,.14)` with a
  `rgba(61,220,132,.45)` border, a **trash / clear-all glyph** + "Kill all" label in accent. **Same vertical
  rhythm** as the cards. When **focused**, it fills **solid accent** with the focus ring/glow and the glyph
  inverts to dark.

**Interaction states to implement (all D-pad):**
| Input | Action |
|---|---|
| **DPAD_RIGHT** | move focus toward Kill All (RTL: right = newer/Kill All side) |
| **DPAD_LEFT** | move focus toward older apps |
| **DPAD_CENTER / OK on a card** | **switch to that task** (`moveTaskToFront`); play brief launch transition, then finish overlay |
| **OK on Kill All** | **remove all tasks**; cards fly up staggered (~28ms each), then → Empty state |
| **DPAD_UP / DOWN on a focused card** | **Stage 1 "peek"** — nudge the card ~46px in that direction, show an accent chevron hint |
| **same direction again** | **Stage 2** — card **flies off-screen** (~330ms) and that **single task is removed** (`removeTask(taskId)`); focus moves to the neighbor. (This is D-pad swipe-to-dismiss — recreate the **mid-dismiss** state: card translated ~300px out, fading, accent ring still on, motion streak behind it.) |
| **changing focus** | cancels any pending peek |
| **BACK** | **close the whole overlay** (finish activity, resume underlying app) |

Dismiss/peek implementation reference: `dismissStep()`, `removeTask()` in `tv/app.js`. CSS states:
`.card.peek-up/.peek-down`, `.card.flyout-up/.flyout-down`, `.card.midflight`.

---

### Surface 2 — SETTINGS
**File:** scene "Settings". Use a **leanback settings list** (left-aligned rows in LTR reading, but the
whole app is RTL so rows align to the start/right edge; English row text stays LTR). Vertical D-pad moves
between rows; **clear focus highlight** = surface-2 fill + accent border + accent-tint glow.

Exactly four options (no more):
1. **Show Kill All** — **toggle** on/off. Controls whether the Kill All tile appears in the overlay.
   Persist `showKillAll`. (When off, the overlay starts focused on task #1.)
2. **Size of task** — **stepped segmented control**: Small / Medium / Large. Selected segment = solid
   accent, dark text. Drives `--card-size` (142/180/230). DPAD_LEFT/RIGHT steps; persist `size`.
3. **Shape of task** — **segmented**: Square / Rounded / Circle, **with a live shape preview swatch**
   next to the control that updates immediately (radius 16/32/50%). Persist `shape`.
4. **Exceptions** — a **row that opens a sub-screen**: a **scrollable list of installed apps**, each with a
   **toggle** to exclude it from the overlay. Excluded apps are filtered out of the task list. Source apps
   from `PackageManager` (launchable apps), show real icon + label + a category/system tag, and a
   right-aligned state text ("Shown" / "Hidden from recents"). Persist an **exceptions set** of package names.
   The Settings "Exceptions" row shows a summary ("N apps hidden"). List must D-pad scroll with the focused
   row kept in view (see `applyExcFocus()`), with a bottom fade affordance.

Persist all of this in **DataStore** (`SettingsStore`). The overlay reads these on every open.

---

### Surface 3 — STATE: Shizuku not running / permission required
**File:** scene "Shizuku".

- Centered card: a shield/permission **icon**, a **"Permission required"** pill (accent tint), a title
  **"Shizuku isn't running"**, and a short body explaining the app reads the task list through Shizuku and
  the user should start the service & grant access.
- **One primary focusable button:** **"Open Shizuku & grant"** (focus = solid accent, dark text, glow).
  A secondary **"Retry"** button is fine. Primary action should:
  - if Shizuku app is installed → launch it / trigger `Shizuku.requestPermission`;
  - re-check binder + permission on resume and auto-advance to the overlay once granted.
- This is the gate shown whenever `pingBinder()` fails or permission is missing.

---

### Surface 4 — STATE: Empty (no recent tasks)
**File:** scene "Empty".

- Same translucent overlay, but instead of the carousel show a **subtle centered message**: a faint
  dashed-square-in-circle glyph, **"No recent apps"** (700, ~46px), and a dim subline
  ("Apps you open will appear here.").
- **Kill All is hidden/disabled** in this state (nothing to clear).
- BACK still closes the overlay.

---

### Surface 5 — PINNED APPS (new)
Screens **07, 08, 09, 11, 14, 15**.
- Users can pin any installed app. Pinned apps **always appear in the row**, running or not, grouped in a subtle rounded container labelled **"Pinned"** with a pin icon.
- A pinned app that is also running appears **once**, in the pinned group, with a small **running dot** under it (hidden when focused). It is removed from the normal task list.
- **Pinned position** setting: **Left (default)** = far (end) side of the RTL row, after the oldest task. **Right** = immediately next to Kill All, before task #1.
- **Pinned icon size** setting: **Small (default)** = 72% of the task card size · **Same as tasks**.
- Pinned cards carry a small **pin badge** on their top corner so they read differently from tasks.
- Pinned cards ignore UP/DOWN (they can't be swiped away). OK opens them: `moveTaskToFront` if running, otherwise launch the package's leanback launch intent.
- **Kill All** removes tasks only. Pins stay.
- **Pinned apps list** (Settings → Pinned apps): same layout as Exceptions, toggle = pinned, a bulk **Unpin all** button. Pin order is the order the user pinned them in.
- Persist `pinned: List<String>` (ordered), `pinPosition`, `pinSize` in DataStore.

### Surface 6 — LONG-PRESS OK MENU (new)
Screens **05, 06**.
- **Holding OK ≥ 550 ms** on any task or pinned card opens a **balloon menu above that card**, with a downward tip pointing at it. A short press still opens the app (fire on KeyUp, not KeyDown).
- While open, the row slides down 18% of the screen height (97dp), the other cards and Kill All dim to ~32%, and the focused card's name label and the footer hints hide.
- Header: small app icon, app name, status line ("Pinned · Running" / "Running" / "Not running").
- Items (UP/DOWN to move, OK to run, BACK to close, LEFT/RIGHT do nothing):
  1. **Pin to row** / **Unpin**
  2. **Add to exceptions**: the card flies up and is hidden from the overlay. A toast confirms it.
  3. **App info**: opens `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for the package and closes the overlay.
- Focused item = solid accent fill + dark text (same as other TV buttons).
- A white pill toast near the bottom confirms each action (~1.8 s).

### Surface 7 — EXCEPTIONS: BULK ACTIONS (new)
Screens **16, 17, 18**.
- A bar above the list holds **Hide all** (exclude every installed app) and **Show all** (clear the exceptions), plus a count, e.g. "1 of 12 hidden".
- The bar is reached with UP from the first row. LEFT/RIGHT move between the buttons.
- If everything is hidden and nothing is pinned, the overlay shows the Empty state.

### Surface 4b — EMPTY WITH PINS (new)
Screen **11**: no running tasks but some pins. Kill All is hidden. In place of the tasks, a dashed placeholder reads **"No recent apps / Pinned apps are still one press away."** The pinned group stays focusable. The full Empty state (screen 10) shows only when there are no tasks **and** no pins.

### Overlay footer (new)
Key hints along the bottom of the overlay: **OK** Open · **Hold OK** Options · **↑ ↓** Twice to remove · **Back** Close. They hide while the menu is open and in the Empty state.

---

## 5. State & data
- **SettingsStore (DataStore):** `showKillAll: Boolean = true`, `size: enum{SMALL,MEDIUM,LARGE}=MEDIUM`,
  `shape: enum{SQUARE,ROUNDED,CIRCLE}=ROUNDED`, `exceptions: Set<String>` (package names).
- **RecentTasksRepository:** `observeTasks(): Flow<List<RecentTask>>` filtered by exceptions; actions
  `switchTo(taskId)`, `dismiss(taskId)`, `killAll()`. Backed by Shizuku privileged calls.
- **ShizukuManager:** `state: Flow<{ binderAlive, permissionGranted }>`; `requestPermission()`,
  `openShizuku()`. The overlay routes to Surface 3 whenever not ready.
- **Focus model:** the overlay maintains a focus index over `[killAll?, task#1, task#2, …]`. Default focus =
  task #1 when Kill All is shown (index 1), else task #1 (index 0). Removing a task keeps focus on the
  nearest neighbor. Recreate the logic in `tv/app.js` (`defaultOvFocus`, `moveFocus`, `removeTask`).

---

## 6. Motion spec (match the prototype)
- Scrim fade-in: 320ms ease.
- Carousel translate / focus settle: ~340ms `cubic-bezier(.22,.61,.36,1)`.
- Focus scale (card 1.15×, kill-all 1.10×): ~260ms same easing.
- Peek nudge: ~46px, instant-ish (driven by the same transform transition).
- Fly-off dismiss: translate ±700px + fade, ~330ms `ease-in`; remove task on completion.
- Kill-all clear: per-card fly-up staggered 28ms; → Empty after ~520ms.
- App-switch launch flash: ~850ms then finish activity.

---

## 7. Acceptance checklist
- [ ] Overlay appears as a **translucent dialog** over the live app (system dim ~0.66), not a full screen.
- [ ] **RTL**: Kill All far-right, #1 to its left, older leftward; carousel scrolls and **clamps** so Kill All stays visible.
- [ ] **Icon-only** cards using **real launcher icons**; focused card = 1.15× + accent ring/glow + name label; others dim/no label.
- [ ] Kill All tile is **larger**, accent-tinted, trash glyph, fills solid accent on focus.
- [ ] DPAD L/R focus, **OK** switches task, **OK** on Kill All clears all → Empty.
- [ ] **UP/DOWN** = two-stage D-pad swipe-to-dismiss removing a single task; mid-dismiss state rendered.
- [ ] **BACK** closes overlay.
- [ ] Settings: Overlay (Show Kill All, Size, Shape) · Pinned apps (list, Position, Icon size) · Hidden apps (Exceptions) — all persisted.
- [ ] **Shizuku** gate state when not running / not granted, with working **grant** action; overlay only renders when ready.
- [ ] **Empty** state with Kill All hidden.
- [ ] **No** trigger/"how to open" setting. Overlay launchable via exported `RecentsActivity` + static & dynamic **shortcut**; component name documented.
- [ ] Pinned apps: group + badge + running dot; Left (default) / Right position; Small (default) / Same size; Kill All keeps pins; Pinned apps list with Unpin all.
- [ ] Hold OK ≥550ms opens the balloon menu (Pin/Unpin, Add to exceptions, App info); a short press still opens the app.
- [ ] Exceptions: Hide all / Show all bulk bar + count.
- [ ] Empty with pins (screen 11) vs full Empty (screen 10).
- [ ] Everything reachable by **D-pad only**; focus is the strongest visual cue on every screen.

---

## 8. Files in this bundle

### Screenshots (`screenshots/`, 1920×1080) — `00-all-screens.png` shows all 27
01 overlay · 02 Kill All focused · 03 dismiss peek · 04 dismiss mid-flight · 05 hold-OK menu (task) · 06 hold-OK menu (pinned) · 07 pinned left (default) · 08 pinned right · 09 pinned same size · 10 empty · 11 empty with pins · 12 settings · 13 shape = circle · 14 settings, pinned section · 15 pinned apps list · 16 exceptions · 17 exceptions bulk · 18 exceptions all hidden · 19 Shizuku gate · 20 select press · 21 select zooming · 22 select full · 23 settings background color · 24 background blur · 25 background color · 26 background image · 27 settings background image.

The header ("Recent apps" + count) is **centered** on every overlay screen.

### Jetpack Compose (`compose/`) — Compose for TV, Kotlin
Copy `compose/*.kt` into `app/src/main/java/app/recents/tv/` (keep sub-folders as packages) and `compose/res/` into `app/src/main/res/`. Merge `AndroidManifest.xml` and `build.gradle.kts`.

| File | What it is |
|---|---|
| `theme/Color.kt` · `Type.kt` · `Dimens.kt` · `Theme.kt` · `Focus.kt` | Tokens, type scale, sizes/shapes/motion enums, `RecentsTheme` (forces RTL), `Modifier.focusRing` |
| `components/Overlay.kt` | `TaskCard` (focus, two-stage dismiss, hold-OK), `KillAllTile`, `EmptyRecents` |
| `components/Pins.kt` | `PinnedGroup`, `PinBadge`, `RunningDot`, `EmptyRecentsSlot` |
| `components/ContextMenu.kt` | `TaskContextMenu` balloon (Popup anchored above the card) |
| `components/SelectZoom.kt` | Tile → full-screen select animation |
| `components/Background.kt` | Dim / Blur / Color / Image backdrop + window flags |
| `components/Settings.kt` | `SettingsRow`, `RecentsToggle`, `Segmented`, `ShapePreview`, `RecentsButton` |
| `screens/RecentsScreen.kt` | Overlay screen (01–11, 20–22, 24–26) |
| `screens/SettingsScreen.kt` | Settings (12–14, 23, 27) |
| `screens/ExceptionsScreen.kt` | `ExceptionsScreen` + `PinnedAppsScreen` with bulk bars (15–18) |
| `screens/ShizukuGateScreen.kt` | Permission gate (19) |
| `model/UiModels.kt` | `RecentTask`, `InstalledApp`, `SettingsState`, `TaskAction`, `RecentsUiState` |
| `data/Contracts.kt` | Interfaces to implement: `ShizukuManager`, `RecentTasksRepository`, `AppCatalog`, `SettingsStore`, `BackgroundImageLoader` + `Graph` |
| `RecentsViewModel.kt` | Combines Shizuku state, tasks, pins, settings → `RecentsUiState`; all actions |
| `RecentsActivity.kt` | Dialog-style overlay host |
| `SettingsActivity.kt` | Settings host: Settings → Pinned apps / Exceptions, image picker |
| `RecentsApp.kt` | Application: wire `Graph`, publish dynamic shortcut |
| `preview/SampleData.kt` · `Previews.kt` | `@Preview` for every screen at 1080p TV, using the prototype's sample apps |
| `AndroidManifest.xml` | Activities, LEANBACK_LAUNCHER, `SHOW_RECENTS` action, Shizuku provider |
| `res/xml/shortcuts.xml` · `res/values/strings.xml` · `res/values/themes.xml` · `res/drawable/*` | Static shortcuts, strings, overlay theme (dim 0.66), icons, banner |
| `build.gradle.kts` | Required dependencies |

**Still to implement (Claude Code):** the five interfaces in `data/Contracts.kt`, then uncomment the wiring in `RecentsApp.kt`. Add Manrope TTFs to `res/font/` (see `Type.kt`).

Units: HTML px ÷ 2 = dp/sp (1080p TV = 960×540 dp).

### Design reference
- `design_reference/Recent Apps TV.html` — interactive prototype (arrow keys, Enter, hold Enter, Esc).
- `design_reference/All Screens.html` — every screen and state on one page.
- `design_reference/tv/` — styles, logic and sample data behind the prototype.
