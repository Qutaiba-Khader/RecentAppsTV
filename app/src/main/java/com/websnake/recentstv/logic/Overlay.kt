package com.websnake.recentstv.logic

/* The overlay's focus engine and key handling as a pure state machine:
   (state, input) → (new state, effects). The Activity runs the effects
   (Shizuku calls, intents, saving, closing); the composables only draw the state.
   Every row item is keyed by its package: a package shows once (TaskList.select). */

enum class Scene { LOADING, OVERLAY, EMPTY, SHIZUKU }

enum class Btn { LEFT, RIGHT, UP, DOWN, OK, BACK, MENU, OTHER }

/** One remote key event. [repeat] > 0 while a key is held: a real remote repeats every ~50 ms. */
data class KeyIn(val btn: Btn, val down: Boolean, val repeat: Int = 0)

const val PEEK_NONE = 0
const val PEEK_UP = -1
const val PEEK_DOWN = 1

/** One focusable thing in the row. */
sealed interface Item {
    val key: String
    val taskId: Int?
    data object KillAll : Item { override val key = "#killall"; override val taskId: Int? = null }
    /** The ⚙ Settings button: above Kill All (reached with UP), or at the right end of the row when Kill All isn't shown. */
    data object Settings : Item { override val key = "#settings"; override val taskId: Int? = null }
    data class Task(val ref: TaskRef) : Item { override val key get() = ref.pkg; override val taskId: Int? get() = ref.taskId }
    /** A pinned app; [taskId] is its running task, null when it isn't running. */
    data class Pin(val pkg: String, override val taskId: Int?) : Item {
        override val key get() = pkg
        val running get() = taskId != null
    }
}

/** Long-press menu rows, from the card up (1.9: drawn bottom-up — PIN sits nearest the card and is focused first).
 *  1.8: actions on that one app only — Settings left (it has the ⚙), Close came. */
enum class CtxOption { PIN, EXCEPTION, APP_INFO, CLOSE }

enum class FlyReason { DISMISS, EXCEPTION }

data class OvState(
    val scene: Scene = Scene.LOADING,
    val all: List<TaskRef> = emptyList(),     // running apps shown (exceptions already removed), newest first
    val pinned: List<String> = emptyList(),   // pinned apps shown, in pin order
    val pinRight: Boolean = false,            // pinned group next to Kill All instead of at the far end
    val showKillAll: Boolean = true,
    val focus: Int = 0,                       // index into [items]: 0 = rightmost (RTL)
    val peek: Int = PEEK_NONE,                // stage-1 dismiss on the focused card (direction)
    val flying: String? = null,               // package whose card is flying off
    val flyDir: Int = PEEK_UP,
    val flyReason: FlyReason = FlyReason.DISMISS,
    val killing: Boolean = false,             // kill-all animation running
    val ctxPkg: String? = null,               // long-press menu target
    val ctxSel: Int = 0,
    val longPress: Boolean = false,           // this OK press already did its job (opened the menu / ran a menu row)
    val pressing: Boolean = false,            // OK is down on a card (press-in feedback)
    val gear: Boolean = false,                // focus is on the ⚙ button above Kill All
    val busy: Boolean = false,                // switching / clearing: ignore input
    val selecting: String? = null,            // package chosen with OK: the select zoom is playing
    val shzFocus: Int = 0,                    // gate: 0 = open & grant, 1 = retry
    val closing: Boolean = false,             // exit animation running
) {
    /** Running apps that are not pinned, newest first. */
    val tasks: List<TaskRef> by lazy { all.filter { it.pkg !in pinned } }

    val pins: List<Item.Pin> by lazy { pinned.map { p -> Item.Pin(p, all.firstOrNull { it.pkg == p }?.taskId) } }

    val hasKillAll get() = showKillAll && tasks.isNotEmpty()

    /** Logical order, right → left: [Kill All] [pins if right] [tasks…] [pins if left].
     *  The ⚙ button sits above Kill All; without Kill All it takes the first place in the row. */
    val items: List<Item> by lazy {
        buildList {
            if (!hasKillAll) add(Item.Settings)
            if (hasKillAll) add(Item.KillAll)
            if (pinRight) addAll(pins)
            tasks.forEach { add(Item.Task(it)) }
            if (!pinRight) addAll(pins)
        }
    }

    /** No running tasks but pins: a placeholder takes the tasks' place (not focusable). */
    val emptySlot get() = tasks.isEmpty() && pins.isNotEmpty()

    val itemCount get() = items.size
    fun itemAt(i: Int): Item? = items.getOrNull(i)
    /** The ⚙ above Kill All counts only while Kill All is shown and focused. */
    val gearFocused get() = gear && scene == Scene.OVERLAY && hasKillAll && itemAt(focus) == Item.KillAll
    val focusedItem: Item? get() = if (scene != Scene.OVERLAY) null else if (gearFocused) Item.Settings else itemAt(focus)
    fun indexOf(key: String) = items.indexOfFirst { it.key == key }
    val ctxItem: Item? get() = ctxPkg?.let { k -> items.firstOrNull { it.key == k } }
}

sealed interface Effect {
    /** Open the app: bring [taskId] to front, or launch [pkg] when it isn't running. */
    data class Switch(val pkg: String, val taskId: Int?) : Effect
    data class Dismiss(val task: TaskRef) : Effect
    data class KillAll(val tasks: List<TaskRef>) : Effect
    data class AddException(val pkg: String) : Effect
    data class SetPin(val pkg: String, val pinned: Boolean) : Effect
    data class AppInfo(val pkg: String) : Effect
    data object OpenSettings : Effect
    /** OK went down on a card: call [Overlay.holdElapsed] after [Overlay.HOLD_MS] unless it is released first. */
    data object ArmHold : Effect
    data object Close : Effect
    data object GateAction : Effect          // open Shizuku or ask for permission, then reload
}

object Overlay {

    /** Holding OK this long on a card opens its menu (design: ≥ 550 ms). Timed, not counted in key repeats. */
    const val HOLD_MS = 550L

    /** Task #1 first; with no tasks, the first pin. */
    fun defaultFocus(s: OvState): Int {
        val t = s.items.indexOfFirst { it is Item.Task }
        return if (t >= 0) t else s.items.indexOfFirst { it is Item.Pin }.coerceAtLeast(0)
    }

    /** A fresh list arrived: route the scene and clear every transient guard. */
    fun loaded(s: OvState, all: List<TaskRef>, pinned: List<String>, showKillAll: Boolean, pinRight: Boolean): OvState =
        settle(OvState(scene = Scene.OVERLAY, all = all, pinned = pinned, showKillAll = showKillAll, pinRight = pinRight), null)

    fun needShizuku(s: OvState) = OvState(scene = Scene.SHIZUKU, showKillAll = s.showKillAll)

    /**
     * 1.8: the app was launched again while the overlay was on screen — the same remote button (a mapped key, a
     * tvQuickActions shortcut) pressed a second time. That closes it, as Back would; nothing while it is already
     * closing or opening an app.
     */
    fun relaunched(s: OvState): Pair<OvState, List<Effect>> =
        if (s.closing || s.selecting != null) s to emptyList()
        else s.copy(closing = true, pressing = false, ctxPkg = null) to listOf(Effect.Close)

    /** Route to Empty when nothing is left; otherwise keep focus on [follow] if it is still there. */
    private fun settle(s: OvState, follow: String?, at: Int = -1): OvState {
        if (s.tasks.isEmpty() && s.pins.isEmpty()) return s.copy(scene = Scene.EMPTY, focus = 0, peek = PEEK_NONE)
        val n = s.copy(scene = Scene.OVERLAY, gear = false)
        val i = follow?.let { n.indexOf(it) } ?: -1
        val focus = when {
            i >= 0 -> i
            at >= 0 -> at.coerceIn(0, n.itemCount - 1)
            else -> defaultFocus(n)
        }
        return n.copy(focus = focus)
    }

    fun key(s: OvState, k: KeyIn): Pair<OvState, List<Effect>> {
        if (s.closing || s.selecting != null) return s to emptyList()
        if (k.down) when (k.btn) {
            Btn.BACK -> return if (s.ctxPkg != null) s.copy(ctxPkg = null) to emptyList()
                else s.copy(closing = true, pressing = false) to listOf(Effect.Close)
            Btn.MENU -> return s to listOf(Effect.OpenSettings)
            else -> {}
        }
        if (s.ctxPkg != null) {
            // The OK release that follows the long-press lands here, with the menu open:
            // it ends the hold, or the next real OK tap would be eaten.
            if (!k.down) return (if (k.btn == Btn.OK && s.longPress) s.copy(longPress = false) else s) to emptyList()
            // OK still held from the press that opened the menu: its repeats must not run a row
            // (a real remote sends one every ~50 ms — they used to pin the app at once).
            if (k.btn == Btn.OK && (k.repeat > 0 || s.longPress)) return s to emptyList()
            return ctxKey(s, k.btn)
        }

        // Overlay OK: hold = context menu; tap (released) = activate.
        if (s.scene == Scene.OVERLAY && k.btn == Btn.OK) {
            // ignored while changing — but a release still ends a hold, or it would eat the next tap
            if (s.busy || s.killing || s.flying != null) return s.copy(pressing = false, longPress = s.longPress && k.down) to emptyList()
            val item = s.focusedItem
            val card = item is Item.Task || item is Item.Pin
            if (k.down) {
                // repeats of a held OK do nothing: the hold is timed (ArmHold → holdElapsed)
                if (k.repeat > 0) return s to emptyList()
                return s.copy(pressing = card) to (if (card) listOf(Effect.ArmHold) else emptyList())
            }
            val up = s.copy(pressing = false)
            return if (s.longPress) up.copy(longPress = false) to emptyList() else activate(up)
        }
        if (!k.down) return s to emptyList()
        return when (s.scene) {
            Scene.LOADING -> s to emptyList()
            Scene.SHIZUKU -> gateKey(s, k.btn)
            Scene.EMPTY -> if (k.btn == Btn.OK || k.btn == Btn.UP) s to listOf(Effect.OpenSettings) else s to emptyList()
            Scene.OVERLAY -> overlayKey(s, k.btn)
        }
    }

    /** The hold timer fired: OK is still down on the same card → open its menu above it. */
    fun holdElapsed(s: OvState): OvState {
        if (!s.pressing || s.ctxPkg != null || s.closing || s.selecting != null) return s
        val item = s.focusedItem
        if (item !is Item.Task && item !is Item.Pin) return s
        return s.copy(longPress = true, pressing = false, ctxSel = 0, ctxPkg = item.key, peek = PEEK_NONE)
    }

    private fun overlayKey(s0: OvState, b: Btn): Pair<OvState, List<Effect>> {
        // another key while OK is held: the hold no longer counts (it would open the menu on the next card)
        val s = s0.copy(pressing = false)
        // busy/killing: switching or clearing. flying: the dismissed card is still on its way
        // out — a key now would act on the app being removed (found by flowmap P-OVL-36).
        if (s.busy || s.killing || s.flying != null) return s to emptyList()
        return when (b) {
            Btn.RIGHT -> move(s.copy(peek = PEEK_NONE), -1) to emptyList()     // RTL: toward Kill All
            Btn.LEFT -> move(s.copy(peek = PEEK_NONE), +1) to emptyList()      // toward older apps
            // Kill All ↑ = the ⚙ above it; ⚙ ↓ = back to Kill All
            // 1.8: UP on an app opens its menu, as holding OK does (DOWN twice still removes it)
            Btn.UP -> when (val item = s.focusedItem) {
                Item.KillAll -> s.copy(gear = true, peek = PEEK_NONE)
                // 1.9: the card lowered by a first DOWN (about to be removed): UP puts it back, nothing else
                else -> if (s.peek == PEEK_DOWN) s.copy(peek = PEEK_NONE) else upOnCard(s, item)
            } to emptyList()

            Btn.DOWN -> (if (s.gearFocused) s.copy(gear = false) else dismissStep(s, PEEK_DOWN)) to emptyList()
            else -> s to emptyList()
        }
    }

    /** UP on an app or a pin opens its menu (as holding OK does); on the in-row ⚙ it does nothing. */
    private fun upOnCard(s: OvState, item: Item?): OvState =
        if (item is Item.Task || item is Item.Pin) s.copy(ctxPkg = item.key, ctxSel = 0, longPress = false, peek = PEEK_NONE)
        else s

    private fun move(s0: OvState, d: Int): OvState {
        // from the ⚙: RIGHT stays (nothing to its right), LEFT goes on like from Kill All
        if (s0.gearFocused && d < 0) return s0
        val s = s0.copy(gear = false)
        return s.copy(focus = (s.focus + d).coerceIn(0, (s.itemCount - 1).coerceAtLeast(0)))
    }

    /** Two presses in one direction: peek, then fly off. Only running tasks can be swiped away. */
    private fun dismissStep(s: OvState, dir: Int): OvState {
        val item = s.focusedItem as? Item.Task ?: return s
        return if (s.peek == dir) s.copy(peek = PEEK_NONE, flying = item.key, flyDir = dir, flyReason = FlyReason.DISMISS)
        else s.copy(peek = dir)
    }

    private fun activate(s: OvState): Pair<OvState, List<Effect>> {
        val item = s.focusedItem ?: return s to emptyList()
        if (item == Item.Settings) return s to listOf(Effect.OpenSettings)
        if (item == Item.KillAll) return s.copy(busy = true, killing = true, peek = PEEK_NONE) to
            listOf(Effect.KillAll(s.tasks + s.pins.mapNotNull { p -> p.taskId?.let { TaskRef(it, p.pkg) } }))
        // a task, or a pin (its running task, or a launch when it isn't running)
        return s.copy(busy = true, selecting = item.key, peek = PEEK_NONE) to listOf(Effect.Switch(item.key, item.taskId))
    }

    /** The fly-off of [pkg] finished: drop it from the row (and remove the task for real when dismissed). */
    fun flyComplete(s: OvState, pkg: String): Pair<OvState, List<Effect>> {
        if (s.flying != pkg) return s to emptyList()
        val base = s.copy(flying = null, peek = PEEK_NONE)
        return when (s.flyReason) {
            FlyReason.DISMISS -> {
                val task = s.all.firstOrNull { it.pkg == pkg } ?: return settle(base, null, s.focus) to emptyList()
                settle(base.copy(all = s.all - task), null, s.focus) to listOf(Effect.Dismiss(task))
            }
            FlyReason.EXCEPTION ->
                settle(base.copy(all = s.all.filter { it.pkg != pkg }, pinned = s.pinned - pkg), null, s.focus) to emptyList()
        }
    }

    /** The kill-all animation finished: tasks are gone, pins stay (now not running). */
    fun killAllDone(s: OvState): OvState =
        settle(s.copy(all = emptyList(), killing = false, busy = false, peek = PEEK_NONE), null)

    private fun ctxKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> {
        val item = s.ctxItem ?: return s.copy(ctxPkg = null) to emptyList()
        val pkg = item.key
        val last = CtxOption.entries.size - 1
        return when (b) {
            // 1.9: the balloon is drawn bottom-up (row 0 = Pin, nearest the card): UP climbs it; DOWN on the bottom
            // row closes it and the card is as before — a sub-menu of the card
            Btn.UP -> s.copy(ctxSel = (s.ctxSel + 1).coerceAtMost(last)) to emptyList()
            Btn.DOWN -> (if (s.ctxSel <= 0) s.copy(ctxPkg = null, ctxSel = 0) else s.copy(ctxSel = s.ctxSel - 1)) to emptyList()
            Btn.OK -> {
                // longPress = true: this OK's release must not also open the app now under focus
                val closed = s.copy(ctxPkg = null, longPress = true)
                when (CtxOption.entries[s.ctxSel.coerceIn(0, last)]) {
                    CtxOption.PIN -> {
                        val on = pkg !in s.pinned
                        val pinned = if (on) s.pinned + pkg else s.pinned - pkg
                        settle(closed.copy(pinned = pinned), pkg, s.focus) to listOf(Effect.SetPin(pkg, on))
                    }
                    CtxOption.EXCEPTION -> closed.copy(flying = pkg, flyDir = PEEK_UP, flyReason = FlyReason.EXCEPTION) to
                        listOf(Effect.AddException(pkg))
                    CtxOption.APP_INFO -> closed.copy(closing = true) to listOf(Effect.AppInfo(pkg), Effect.Close)
                    // Close = remove that app, as DOWN twice does: a task flies off; a running pin stays pinned, its task goes
                    CtxOption.CLOSE -> if (item is Item.Pin) {
                        val t = s.all.firstOrNull { it.pkg == pkg }
                        if (t == null) closed to emptyList()
                        else settle(closed.copy(all = s.all - t), pkg, s.focus) to listOf(Effect.Dismiss(t))
                    } else closed.copy(flying = pkg, flyDir = PEEK_UP, flyReason = FlyReason.DISMISS) to emptyList()
                }
            }
            else -> s to emptyList()      // LEFT / RIGHT do nothing while the menu is open (design)
        }
    }

    private fun gateKey(s: OvState, b: Btn): Pair<OvState, List<Effect>> = when (b) {
        Btn.LEFT -> s.copy(shzFocus = (s.shzFocus + 1).coerceAtMost(1)) to emptyList()   // RTL: Retry sits left
        Btn.RIGHT -> s.copy(shzFocus = (s.shzFocus - 1).coerceAtLeast(0)) to emptyList()
        Btn.OK -> s to listOf(Effect.GateAction)      // either button advances the flow
        else -> s to emptyList()
    }
}
