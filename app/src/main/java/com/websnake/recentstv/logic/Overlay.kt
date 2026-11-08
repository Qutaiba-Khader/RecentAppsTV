package com.websnake.recentstv.logic

/* The overlay's focus engine and key handling as a pure state machine:
   (state, input) → (new state, effects). The Activity runs the effects
   (Shizuku calls, intents, closing); the composables only draw the state. */

enum class Scene { LOADING, OVERLAY, EMPTY, SHIZUKU }

enum class Btn { LEFT, RIGHT, UP, DOWN, OK, BACK, MENU, OTHER }

/** One remote key event. [repeat] > 0 while a key is held. */
data class KeyIn(val btn: Btn, val down: Boolean, val repeat: Int = 0)

const val PEEK_NONE = -1
const val PEEK_DOWN = 1

enum class CtxOption { ADD_EXCEPTION, KILL_APP, APP_INFO }

data class OvState(
    val scene: Scene = Scene.LOADING,
    val tasks: List<TaskRef> = emptyList(),
    val showKillAll: Boolean = true,
    val focus: Int = 0,                 // index over [killAll?, task#1, task#2 …]
    val peek: Int = PEEK_NONE,          // stage-1 dismiss on the focused card
    val flying: Int? = null,            // taskId currently flying off (stage 2)
    val killing: Boolean = false,       // kill-all animation running
    val gear: Boolean = false,          // settings gear focused
    val ctxTaskId: Int? = null,         // long-press menu target
    val ctxSel: Int = 0,
    val longPress: Boolean = false,     // OK is held and already opened the menu
    val busy: Boolean = false,          // switching / clearing: ignore input
    val shzFocus: Int = 0,              // gate: 0 = open & grant, 1 = retry
    val closing: Boolean = false,       // exit animation running
) {
    val hasKillAll get() = showKillAll && tasks.isNotEmpty()
    val itemCount get() = tasks.size + if (hasKillAll) 1 else 0
    fun isKillAllAt(i: Int) = hasKillAll && i == 0
    fun taskAt(i: Int): TaskRef? = tasks.getOrNull(i - if (hasKillAll) 1 else 0)
    val focusedTask: TaskRef? get() = if (scene == Scene.OVERLAY && !gear) taskAt(focus) else null
    val ctxTask: TaskRef? get() = ctxTaskId?.let { id -> tasks.firstOrNull { it.taskId == id } }
}

sealed interface Effect {
    data class Switch(val task: TaskRef) : Effect
    data class Dismiss(val task: TaskRef) : Effect
    data class KillAll(val tasks: List<TaskRef>) : Effect
    data class KillApp(val task: TaskRef) : Effect
    data class AddException(val task: TaskRef) : Effect
    data class AppInfo(val task: TaskRef) : Effect
    data object OpenSettings : Effect
    data object Close : Effect
    data object GateAction : Effect          // open Shizuku or ask for permission, then reload
}

object Overlay {

    fun defaultFocus(s: OvState) = if (s.hasKillAll) 1 else 0

    /** A fresh task list arrived: route the scene and clear every transient guard. */
    fun loaded(s: OvState, tasks: List<TaskRef>, showKillAll: Boolean): OvState {
        val base = OvState(scene = Scene.LOADING, tasks = tasks, showKillAll = showKillAll)
        return if (tasks.isEmpty()) base.copy(scene = Scene.EMPTY)
        else base.copy(scene = Scene.OVERLAY, focus = defaultFocus(base))
    }

    fun needShizuku(s: OvState) = OvState(scene = Scene.SHIZUKU, showKillAll = s.showKillAll)

    fun key(s: OvState, k: KeyIn): Pair<OvState, List<Effect>> {
        if (s.closing) return s to emptyList()
        if (k.down) when (k.btn) {
            Btn.BACK -> return if (s.ctxTaskId != null) s.copy(ctxTaskId = null) to emptyList()
                else s.copy(closing = true) to listOf(Effect.Close)
            Btn.MENU -> return s to listOf(Effect.OpenSettings)
            else -> {}
        }
        if (s.ctxTaskId != null) {
            // The OK release that follows the long-press lands here, with the menu open:
            // it ends the hold, or the next real OK tap would be eaten.
            if (!k.down) return (if (k.btn == Btn.OK && s.longPress) s.copy(longPress = false) else s) to emptyList()
            return ctxKey(s, k.btn)
        }

        // Overlay OK: hold = context menu; tap (released) = activate.
        if (s.scene == Scene.OVERLAY && k.btn == Btn.OK && !s.gear && !s.busy && !s.killing && s.flying == null) {
            if (k.down) {
                if (k.repeat == 1 && !s.isKillAllAt(s.focus) && s.taskAt(s.focus) != null) {
                    return s.copy(longPress = true, ctxSel = 0, ctxTaskId = s.taskAt(s.focus)!!.taskId, peek = PEEK_NONE) to emptyList()
                }
                return s to emptyList()
            }
            return if (s.longPress) s.copy(longPress = false) to emptyList() else overlayKey(s, Btn.OK)
        }
        if (!k.down) return s to emptyList()
        return when (s.scene) {
            Scene.LOADING -> s to emptyList()
            Scene.SHIZUKU -> gateKey(s, k.btn)
            Scene.EMPTY -> if (k.btn == Btn.OK || k.btn == Btn.UP) s to listOf(Effect.OpenSettings) else s to emptyList()
            Scene.OVERLAY -> overlayKey(s, k.btn)
        }
    }

    private fun overlayKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        // busy/killing: switching or clearing. flying: the dismissed card is still on its way
        // out — an OK now would switch to the app being removed (found by flowmap P-OVL-36).
        if (s.busy || s.killing || s.flying != null) return s to emptyList()
        if (s.gear) return when (b) {
            Btn.DOWN, Btn.LEFT, Btn.RIGHT -> s.copy(gear = false) to emptyList()
            Btn.OK -> s to listOf(Effect.OpenSettings)
            else -> s to emptyList()
        }
        return when (b) {
            Btn.RIGHT -> move(s.copy(peek = PEEK_NONE), -1) to emptyList()     // RTL: toward Kill All
            Btn.LEFT -> move(s.copy(peek = PEEK_NONE), +1) to emptyList()      // toward older apps
            Btn.UP -> (if (s.peek != PEEK_NONE) s.copy(peek = PEEK_NONE) else s.copy(gear = true)) to emptyList()
            Btn.DOWN -> dismissStep(s) to emptyList()
            Btn.OK -> activate(s)
            else -> s to emptyList()
        }
    }

    private fun move(s: OvState, d: Int): OvState =
        s.copy(focus = (s.focus + d).coerceIn(0, (s.itemCount - 1).coerceAtLeast(0)))

    private fun dismissStep(s: OvState): OvState {
        if (s.isKillAllAt(s.focus)) return s
        val task = s.taskAt(s.focus) ?: return s
        return if (s.peek == PEEK_DOWN) s.copy(peek = PEEK_NONE, flying = task.taskId)   // stage 2 — fly off
        else s.copy(peek = PEEK_DOWN)                                                     // stage 1 — peek
    }

    private fun activate(s: OvState): Pair<OvState, List<Effect>> {
        if (s.isKillAllAt(s.focus)) {
            return s.copy(busy = true, killing = true) to listOf(Effect.KillAll(s.tasks))
        }
        val task = s.taskAt(s.focus) ?: return s to emptyList()
        return s.copy(busy = true) to listOf(Effect.Switch(task))
    }

    /** The fly-off animation of [taskId] finished: drop it from the row, then remove it for real. */
    fun flyComplete(s: OvState, taskId: Int): Pair<OvState, List<Effect>> {
        val task = s.tasks.firstOrNull { it.taskId == taskId } ?: return s.copy(flying = null) to emptyList()
        return removeTask(s.copy(flying = null, peek = PEEK_NONE), task) to listOf(Effect.Dismiss(task))
    }

    /** The kill-all animation finished. */
    fun killAllDone(s: OvState) =
        s.copy(tasks = emptyList(), killing = false, busy = false, scene = Scene.EMPTY, focus = 0)

    private fun removeTask(s: OvState, task: TaskRef): OvState {
        val left = s.tasks.filter { it.taskId != task.taskId }
        val n = s.copy(tasks = left)
        if (left.isEmpty()) return n.copy(scene = Scene.EMPTY, focus = 0, peek = PEEK_NONE)
        return n.copy(focus = n.focus.coerceIn(0, n.itemCount - 1))
    }

    private fun ctxKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val task = s.ctxTask ?: return s.copy(ctxTaskId = null) to emptyList()
        val last = CtxOption.entries.size - 1
        return when (b) {
            Btn.UP -> s.copy(ctxSel = (s.ctxSel - 1).coerceAtLeast(0)) to emptyList()
            Btn.DOWN -> s.copy(ctxSel = (s.ctxSel + 1).coerceAtMost(last)) to emptyList()
            Btn.LEFT, Btn.RIGHT -> s.copy(ctxTaskId = null) to emptyList()
            Btn.OK -> {
                val closed = s.copy(ctxTaskId = null)
                when (CtxOption.entries[s.ctxSel]) {
                    CtxOption.ADD_EXCEPTION -> removeTask(closed, task) to listOf(Effect.AddException(task))
                    CtxOption.KILL_APP -> removeTask(closed, task) to listOf(Effect.KillApp(task))
                    CtxOption.APP_INFO -> closed.copy(closing = true) to listOf(Effect.AppInfo(task), Effect.Close)
                }
            }
            else -> s to emptyList()
        }
    }

    private fun gateKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> = when (b) {
        Btn.LEFT -> s.copy(shzFocus = (s.shzFocus + 1).coerceAtMost(1)) to emptyList()   // RTL: Retry sits left
        Btn.RIGHT -> s.copy(shzFocus = (s.shzFocus - 1).coerceAtLeast(0)) to emptyList()
        Btn.OK -> s to listOf(Effect.GateAction)      // either button advances the flow
        else -> s to emptyList()
    }
}
