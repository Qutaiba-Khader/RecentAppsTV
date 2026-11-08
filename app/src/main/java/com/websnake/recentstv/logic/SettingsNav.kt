package com.websnake.recentstv.logic

/* Settings, Pinned apps and Exceptions key handling. The screens are RTL (design):
   rows read from the right, so on a segmented control DPAD_RIGHT steps toward the
   first option and DPAD_LEFT toward the last; OK cycles. Two controls read literally
   (LTR): Pinned position (RIGHT = "Right") and the colour swatches. */

enum class SetRow {
    KILL_ALL, FORCE_STOP, SIZE, SHAPE, ACCENT,          // Overlay
    PINNED_LIST, PIN_POSITION, PIN_SIZE,                // Pinned apps
    BACKGROUND, BG_DIM, BG_COLOR, BG_IMAGE,             // Background
    EXCEPTIONS,                                         // Hidden apps
}

/** Section title shown above the first row of each group. */
fun SetRow.section(): String? = when (this) {
    SetRow.KILL_ALL -> "Overlay"
    SetRow.PINNED_LIST -> "Pinned apps"
    SetRow.BACKGROUND -> "Background"
    SetRow.EXCEPTIONS -> "Hidden apps"
    else -> null
}

enum class SetScreen { SETTINGS, EXCEPTIONS, PINNED, IMAGES }

/** [listFocus] on a list screen: 0 = the bar (bulk actions, or picker apps on IMAGES), 1.. = rows / pictures.
 *  [listAct] = the focused bar button. */
data class SetState(
    val screen: SetScreen = SetScreen.SETTINGS,
    val focus: Int = 0,
    val listFocus: Int = 1,
    val listAct: Int = 0,
)

/** Bulk buttons on the list screens. */
enum class BulkAction { HIDE_ALL, SHOW_ALL, UNPIN_ALL }

sealed interface SetEffect {
    data class Save(val settings: Settings) : SetEffect
    data object LoadApps : SetEffect
    data object LoadImages : SetEffect                    // device pictures + the apps that can pick one
    data class UseImage(val index: Int) : SetEffect        // a picture from the grid (index into the list)
    data class OpenWith(val index: Int) : SetEffect        // hand the choice to that picker app
    data class Bulk(val action: BulkAction) : SetEffect   // the screen shows a toast for it
    data object Finish : SetEffect
}

object SettingsNav {
    const val DIM_STEP = 5
    /** Columns of the picture grid (Settings → Background → Image). */
    const val IMAGE_COLS = 4

    /** Rows shown for [s], in order. The colour / image / dim rows follow the Background choice. */
    fun rows(s: Settings): List<SetRow> = SetRow.entries.filter { r ->
        when (r) {
            SetRow.BG_DIM -> s.background == Backdrop.DIM
            SetRow.BG_COLOR -> s.background == Backdrop.COLOR
            SetRow.BG_IMAGE -> s.background == Backdrop.IMAGE
            else -> true
        }
    }

    fun bulkActions(screen: SetScreen): List<BulkAction> = when (screen) {
        SetScreen.EXCEPTIONS -> listOf(BulkAction.HIDE_ALL, BulkAction.SHOW_ALL)
        SetScreen.PINNED -> listOf(BulkAction.UNPIN_ALL)
        SetScreen.SETTINGS, SetScreen.IMAGES -> emptyList()
    }

    /**
     * [apps] = package names on the list screens, in display order.
     * Returns the new state and what to do (save, load the app list, pick an image, close).
     */
    fun key(st: SetState, s: Settings, b: Btn, apps: List<String>, pickers: Int = 0): Pair<SetState, List<SetEffect>> = when (st.screen) {
        SetScreen.SETTINGS -> settingsKey(st, s, b)
        SetScreen.EXCEPTIONS, SetScreen.PINNED -> listKey(st, s, b, apps)
        SetScreen.IMAGES -> imagesKey(st, b, apps.size, pickers)
    }

    /**
     * The picture screen: a bar of picker apps (Gallery, Files…) above a grid of the device's
     * pictures, [IMAGE_COLS] wide, laid out right-to-left like the rest of Settings (LEFT = next).
     * Without pictures (none, or no access) focus stays on the bar.
     */
    private fun imagesKey(st: SetState, b: Btn, n: Int, pickers: Int): Pair<SetState, List<SetEffect>> {
        val f = st.listFocus.coerceIn(0, n)
        val col = (f - 1) % IMAGE_COLS
        return when (b) {
            Btn.BACK -> st.copy(screen = SetScreen.SETTINGS) to emptyList()
            Btn.UP -> st.copy(listFocus = if (f <= IMAGE_COLS) 0 else f - IMAGE_COLS) to emptyList()
            Btn.DOWN -> st.copy(listFocus = when {
                f == 0 -> if (n > 0) 1 else 0
                f + IMAGE_COLS <= n -> f + IMAGE_COLS
                (f - 1) / IMAGE_COLS < (n - 1) / IMAGE_COLS -> n          // a shorter last row: its last picture
                else -> f
            }) to emptyList()
            Btn.LEFT -> (if (f == 0) st.copy(listAct = (st.listAct + 1).coerceAtMost((pickers - 1).coerceAtLeast(0)))
                else if (col < IMAGE_COLS - 1 && f < n) st.copy(listFocus = f + 1) else st) to emptyList()
            Btn.RIGHT -> (if (f == 0) st.copy(listAct = (st.listAct - 1).coerceAtLeast(0))
                else if (col > 0) st.copy(listFocus = f - 1) else st) to emptyList()
            Btn.OK -> when {
                f > 0 -> st.copy(screen = SetScreen.SETTINGS) to listOf(SetEffect.UseImage(f - 1))
                st.listAct < pickers -> st to listOf(SetEffect.OpenWith(st.listAct))
                else -> st to emptyList()
            }
            else -> st to emptyList()
        }
    }

    private fun settingsKey(st: SetState, s: Settings, b: Btn): Pair<SetState, List<SetEffect>> {
        val rows = rows(s)
        val last = rows.size - 1
        val row = rows[st.focus.coerceIn(0, last)]
        return when (b) {
            Btn.BACK -> st to listOf(SetEffect.Finish)
            Btn.UP -> st.copy(focus = (st.focus - 1).coerceAtLeast(0)) to emptyList()
            Btn.DOWN -> st.copy(focus = (st.focus + 1).coerceAtMost(last)) to emptyList()
            Btn.LEFT, Btn.RIGHT, Btn.OK -> when (row) {
                // the ‹ chevron points left: LEFT / OK open
                SetRow.EXCEPTIONS, SetRow.PINNED_LIST, SetRow.BG_IMAGE ->
                    if (b == Btn.RIGHT) st to emptyList()
                    else when (row) {
                        SetRow.BG_IMAGE -> st.copy(screen = SetScreen.IMAGES, listFocus = 1, listAct = 0) to listOf(SetEffect.LoadImages)
                        SetRow.EXCEPTIONS -> st.copy(screen = SetScreen.EXCEPTIONS, listFocus = 1, listAct = 0) to listOf(SetEffect.LoadApps)
                        else -> st.copy(screen = SetScreen.PINNED, listFocus = 1, listAct = 0) to listOf(SetEffect.LoadApps)
                    }
                else -> {
                    val n = edit(row, s, b)
                    // a Background change adds/removes rows below it; focus stays on this row
                    st to (if (n != s) listOf(SetEffect.Save(n)) else emptyList())
                }
            }
            else -> st to emptyList()
        }
    }

    /** OK cycles (wraps). RTL: RIGHT = previous, LEFT = next. LTR ([ltr]): RIGHT = next. */
    fun <T> step(all: List<T>, cur: T, b: Btn, ltr: Boolean = false): T {
        val i = all.indexOf(cur).coerceAtLeast(0)
        val fwd = if (ltr) Btn.RIGHT else Btn.LEFT
        val back = if (ltr) Btn.LEFT else Btn.RIGHT
        return when (b) {
            Btn.OK -> all[(i + 1) % all.size]
            back -> all[(i - 1).coerceAtLeast(0)]
            fwd -> all[(i + 1).coerceAtMost(all.lastIndex)]
            else -> cur
        }
    }

    fun edit(row: SetRow, s: Settings, b: Btn): Settings = when (row) {
        SetRow.KILL_ALL -> s.copy(showKillAll = !s.showKillAll)
        SetRow.FORCE_STOP -> s.copy(forceStopOnKill = !s.forceStopOnKill)
        SetRow.SIZE -> s.copy(size = step(CardSize.entries, s.size, b))
        SetRow.SHAPE -> s.copy(shape = step(CardShape.entries, s.shape, b))
        SetRow.ACCENT -> s.copy(accent = step(Accent.entries, s.accent, b))
        SetRow.PIN_POSITION -> s.copy(pinPosition = step(PinPosition.entries, s.pinPosition, b, ltr = true))
        SetRow.PIN_SIZE -> s.copy(pinSize = step(PinSize.entries, s.pinSize, b))
        SetRow.BACKGROUND -> s.copy(background = step(Backdrop.entries, s.background, b))
        SetRow.BG_COLOR -> s.copy(bgColor = step((0 until Settings.SWATCHES).toList(), s.bgColor, b, ltr = true))
        // the bar fills from the right (RTL): LEFT and OK raise it, RIGHT lowers it
        SetRow.BG_DIM -> s.copy(bgDim = (s.bgDim + if (b == Btn.RIGHT) -DIM_STEP else DIM_STEP).coerceIn(0, 100))
        SetRow.EXCEPTIONS, SetRow.PINNED_LIST, SetRow.BG_IMAGE -> s
    }

    private fun listKey(st: SetState, s: Settings, b: Btn, apps: List<String>): Pair<SetState, List<SetEffect>> {
        val acts = bulkActions(st.screen)
        val onBar = st.listFocus == 0
        return when (b) {
            Btn.BACK -> st.copy(screen = SetScreen.SETTINGS) to emptyList()
            Btn.UP -> st.copy(listFocus = (st.listFocus - 1).coerceAtLeast(0)) to emptyList()
            Btn.DOWN -> st.copy(listFocus = (st.listFocus + 1).coerceAtMost(apps.size)) to emptyList()
            // on the bar LEFT/RIGHT move between buttons (RTL: LEFT = next); on a row RIGHT = the → back arrow
            Btn.LEFT -> (if (onBar) st.copy(listAct = (st.listAct + 1).coerceAtMost(acts.size - 1)) else st) to emptyList()
            Btn.RIGHT -> (if (onBar) st.copy(listAct = (st.listAct - 1).coerceAtLeast(0)) else st.copy(screen = SetScreen.SETTINGS)) to emptyList()
            Btn.OK -> if (onBar) {
                val a = acts.getOrNull(st.listAct) ?: return st to emptyList()
                st to listOf(SetEffect.Save(bulk(a, s, apps)), SetEffect.Bulk(a))
            } else {
                val pkg = apps.getOrNull(st.listFocus - 1) ?: return st to emptyList()
                val n = if (st.screen == SetScreen.PINNED) togglePin(s, pkg) else toggleException(s, pkg)
                st to listOf(SetEffect.Save(n))
            }
            else -> st to emptyList()
        }
    }

    fun bulk(a: BulkAction, s: Settings, apps: List<String>): Settings = when (a) {
        BulkAction.HIDE_ALL -> s.copy(exceptions = s.exceptions + apps)
        BulkAction.SHOW_ALL -> s.copy(exceptions = emptySet())
        BulkAction.UNPIN_ALL -> s.copy(pinned = emptyList())
    }

    fun toggleException(s: Settings, pkg: String): Settings =
        s.copy(exceptions = if (pkg in s.exceptions) s.exceptions - pkg else s.exceptions + pkg)

    /** Pin order = the order the user pinned them in: a new pin goes last. */
    fun togglePin(s: Settings, pkg: String): Settings =
        s.copy(pinned = if (pkg in s.pinned) s.pinned - pkg else s.pinned + pkg)

    fun setPin(s: Settings, pkg: String, on: Boolean): Settings =
        s.copy(pinned = if (on) (s.pinned - pkg) + pkg else s.pinned - pkg)
}
