package com.websnake.recentstv.logic

/* Pure settings model + codec. No Android or Compose types, so the rules can be
   tested on the JVM; the UI maps these enums to dp / colours in ui/theme. */

enum class CardSize { SMALL, MEDIUM, LARGE }
enum class CardShape { SQUARE, ROUNDED, CIRCLE }
enum class Accent { GREEN, BLUE, PURPLE, AMBER, PINK }

/** Settings → Pinned position. LEFT (default) = far end of the RTL row; RIGHT = next to Kill All. */
enum class PinPosition { LEFT, RIGHT }

/** Settings → Pinned icon size. SMALL (default) = 72 % of a task card. */
enum class PinSize(val factor: Float) { SMALL(0.72f), SAME(1f) }

/** Settings → Background: what sits behind the row. */
enum class Backdrop { DIM, BLUR, COLOR, IMAGE }

data class Settings(
    val showKillAll: Boolean = true,
    val size: CardSize = CardSize.MEDIUM,
    val shape: CardShape = CardShape.ROUNDED,
    val exceptions: Set<String> = emptySet(),
    val accent: Accent = Accent.GREEN,
    val bgDim: Int = DEFAULT_DIM,            // DIM backdrop strength, 0..100 %
    val forceStopOnKill: Boolean = true,
    val pinned: List<String> = emptyList(),  // ordered: the order the user pinned them in
    val pinPosition: PinPosition = PinPosition.LEFT,
    val pinSize: PinSize = PinSize.SMALL,
    val background: Backdrop = Backdrop.DIM,
    val bgColor: Int = 0,                    // index into the colour swatches
    val bgImage: String? = null,             // display name of the picked image (the copy lives in app storage)
) {
    companion object {
        const val DEFAULT_DIM = 66           // design: scrim rgba(0,0,0,.66)
        const val SWATCHES = 5               // Graphite, Midnight, Forest, Plum, Black
    }
}

/** Stored keys. The v1 names are kept, so existing settings carry over. */
object SettingsKeys {
    const val SHOW_KILL_ALL = "show_kill_all"
    const val SIZE = "size"
    const val SHAPE = "shape"
    const val EXCEPTIONS = "exceptions"
    const val ACCENT = "accent"
    const val BG_DIM = "bg_dim_pct"
    const val FORCE_STOP = "force_stop_on_kill"
    const val PINNED = "pinned"              // package names joined by '\n' (a string keeps the order)
    const val PIN_POSITION = "pin_position"
    const val PIN_SIZE = "pin_size"
    const val BACKGROUND = "background"
    const val BG_COLOR = "bg_color"
    const val BG_IMAGE = "bg_image"
}

object SettingsCodec {
    /** Build [Settings] from stored values; anything missing or unreadable falls back to its default. */
    fun decode(get: (String) -> Any?): Settings {
        val d = Settings()
        @Suppress("UNCHECKED_CAST")
        return Settings(
            showKillAll = get(SettingsKeys.SHOW_KILL_ALL) as? Boolean ?: d.showKillAll,
            size = enumOr(get(SettingsKeys.SIZE), d.size),
            shape = enumOr(get(SettingsKeys.SHAPE), d.shape),
            exceptions = (get(SettingsKeys.EXCEPTIONS) as? Set<String>) ?: d.exceptions,
            accent = enumOr(get(SettingsKeys.ACCENT), d.accent),
            bgDim = ((get(SettingsKeys.BG_DIM) as? Int) ?: d.bgDim).coerceIn(0, 100),
            forceStopOnKill = get(SettingsKeys.FORCE_STOP) as? Boolean ?: d.forceStopOnKill,
            pinned = decodeList(get(SettingsKeys.PINNED)),
            pinPosition = enumOr(get(SettingsKeys.PIN_POSITION), d.pinPosition),
            pinSize = enumOr(get(SettingsKeys.PIN_SIZE), d.pinSize),
            background = enumOr(get(SettingsKeys.BACKGROUND), d.background),
            bgColor = ((get(SettingsKeys.BG_COLOR) as? Int) ?: d.bgColor).coerceIn(0, Settings.SWATCHES - 1),
            bgImage = (get(SettingsKeys.BG_IMAGE) as? String)?.takeIf { it.isNotBlank() },
        )
    }

    /** Every stored value for [s], keyed like [decode] reads them. */
    fun encode(s: Settings): Map<String, Any> = mapOf(
        SettingsKeys.SHOW_KILL_ALL to s.showKillAll,
        SettingsKeys.SIZE to s.size.name,
        SettingsKeys.SHAPE to s.shape.name,
        SettingsKeys.EXCEPTIONS to s.exceptions,
        SettingsKeys.ACCENT to s.accent.name,
        SettingsKeys.BG_DIM to s.bgDim.coerceIn(0, 100),
        SettingsKeys.FORCE_STOP to s.forceStopOnKill,
        SettingsKeys.PINNED to s.pinned.joinToString("\n"),
        SettingsKeys.PIN_POSITION to s.pinPosition.name,
        SettingsKeys.PIN_SIZE to s.pinSize.name,
        SettingsKeys.BACKGROUND to s.background.name,
        SettingsKeys.BG_COLOR to s.bgColor.coerceIn(0, Settings.SWATCHES - 1),
        SettingsKeys.BG_IMAGE to (s.bgImage ?: ""),
    )

    /** Ordered, blank-free, each package once. */
    private fun decodeList(raw: Any?): List<String> {
        val text = raw as? String ?: return emptyList()
        return text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    }

    private inline fun <reified E : Enum<E>> enumOr(raw: Any?, default: E): E =
        (raw as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    fun exceptionsSummary(n: Int): String =
        if (n <= 0) "No apps hidden" else "$n ${if (n == 1) "app" else "apps"} hidden"

    fun pinnedSummary(n: Int): String =
        if (n <= 0) "No apps pinned" else "$n ${if (n == 1) "app" else "apps"} pinned"

    /** Overlay header line: "7 apps · 3 pinned", "3 pinned", "1 app" or "". */
    fun countLabel(tasks: Int, pins: Int = 0): String = listOfNotNull(
        tasks.takeIf { it > 0 }?.let { "$it ${if (it == 1) "app" else "apps"}" },
        pins.takeIf { it > 0 }?.let { "$it pinned" },
    ).joinToString(" · ")
}
