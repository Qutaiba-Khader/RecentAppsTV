package com.websnake.recentstv.logic

/* Settings + Exceptions key handling. The screens are RTL (design): rows read
   from the right, so DPAD_RIGHT steps toward the first option, DPAD_LEFT toward
   the last; OK cycles. */

enum class SetRow { KILL_ALL, FORCE_STOP, SIZE, SHAPE, ACCENT, BG_DIM, BG_TINT, EXCEPTIONS }

enum class SetScreen { SETTINGS, EXCEPTIONS }

data class SetState(val screen: SetScreen = SetScreen.SETTINGS, val focus: Int = 0, val excFocus: Int = 0)

sealed interface SetEffect {
    data class Save(val settings: Settings) : SetEffect
    data object LoadApps : SetEffect
    data object Finish : SetEffect
}

object SettingsNav {
    const val DIM_STEP = 5

    /**
     * [apps] = package names on the Exceptions screen, in display order.
     * Returns the new state and what to do (save, load the app list, close).
     */
    fun key(st: SetState, s: Settings, b: Btn, apps: List<String>): Pair<SetState, List<SetEffect>> = when (st.screen) {
        SetScreen.SETTINGS -> settingsKey(st, s, b)
        SetScreen.EXCEPTIONS -> excKey(st, s, b, apps)
    }

    private fun settingsKey(st: SetState, s: Settings, b: Btn): Pair<SetState, List<SetEffect>> {
        val last = SetRow.entries.size - 1
        val row = SetRow.entries[st.focus.coerceIn(0, last)]
        return when (b) {
            Btn.BACK -> st to listOf(SetEffect.Finish)
            Btn.UP -> st.copy(focus = (st.focus - 1).coerceAtLeast(0)) to emptyList()
            Btn.DOWN -> st.copy(focus = (st.focus + 1).coerceAtMost(last)) to emptyList()
            Btn.LEFT, Btn.RIGHT, Btn.OK -> {
                if (row == SetRow.EXCEPTIONS) {
                    if (b == Btn.RIGHT) st to emptyList()     // the ‹ chevron points left: LEFT/OK open
                    else st.copy(screen = SetScreen.EXCEPTIONS, excFocus = 0) to listOf(SetEffect.LoadApps)
                } else {
                    val n = edit(row, s, b)
                    st to (if (n != s) listOf(SetEffect.Save(n)) else emptyList())
                }
            }
            else -> st to emptyList()
        }
    }

    /** OK cycles (wraps); RIGHT = previous (start side in RTL); LEFT = next. */
    fun <T> step(all: List<T>, cur: T, b: Btn): T {
        val i = all.indexOf(cur).coerceAtLeast(0)
        return when (b) {
            Btn.OK -> all[(i + 1) % all.size]
            Btn.RIGHT -> all[(i - 1).coerceAtLeast(0)]
            Btn.LEFT -> all[(i + 1).coerceAtMost(all.lastIndex)]
            else -> cur
        }
    }

    fun edit(row: SetRow, s: Settings, b: Btn): Settings = when (row) {
        SetRow.KILL_ALL -> s.copy(showKillAll = !s.showKillAll)
        SetRow.FORCE_STOP -> s.copy(forceStopOnKill = !s.forceStopOnKill)
        SetRow.SIZE -> s.copy(size = step(CardSize.entries, s.size, b))
        SetRow.SHAPE -> s.copy(shape = step(CardShape.entries, s.shape, b))
        SetRow.ACCENT -> s.copy(accent = step(Accent.entries, s.accent, b))
        SetRow.BG_TINT -> s.copy(bgTint = step(Tint.entries, s.bgTint, b))
        // the bar fills from the right (RTL): LEFT and OK raise it, RIGHT lowers it
        SetRow.BG_DIM -> s.copy(bgDim = (s.bgDim + if (b == Btn.RIGHT) -DIM_STEP else DIM_STEP).coerceIn(0, 100))
        SetRow.EXCEPTIONS -> s
    }

    private fun excKey(st: SetState, s: Settings, b: Btn, apps: List<String>): Pair<SetState, List<SetEffect>> = when (b) {
        Btn.BACK, Btn.RIGHT -> st.copy(screen = SetScreen.SETTINGS) to emptyList()   // the → arrow = back
        Btn.UP -> st.copy(excFocus = (st.excFocus - 1).coerceAtLeast(0)) to emptyList()
        Btn.DOWN -> st.copy(excFocus = (st.excFocus + 1).coerceAtMost((apps.size - 1).coerceAtLeast(0))) to emptyList()
        Btn.OK -> {
            val pkg = apps.getOrNull(st.excFocus)
            if (pkg == null) st to emptyList()
            else st to listOf(SetEffect.Save(toggleException(s, pkg)))
        }
        else -> st to emptyList()
    }

    fun toggleException(s: Settings, pkg: String): Settings =
        s.copy(exceptions = if (pkg in s.exceptions) s.exceptions - pkg else s.exceptions + pkg)
}
