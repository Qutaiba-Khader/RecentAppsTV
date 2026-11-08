package com.websnake.recentstv.logic

/* Pure settings model + codec. No Android or Compose types, so the rules can be
   tested on the JVM; the UI maps these enums to dp / colours in ui/theme. */

enum class CardSize { SMALL, MEDIUM, LARGE }
enum class CardShape { SQUARE, ROUNDED, CIRCLE }
enum class Accent { GREEN, BLUE, PURPLE, AMBER, PINK }
enum class Tint { BLACK, CHARCOAL, NAVY, GREY }

data class Settings(
    val showKillAll: Boolean = true,
    val size: CardSize = CardSize.MEDIUM,
    val shape: CardShape = CardShape.ROUNDED,
    val exceptions: Set<String> = emptySet(),
    val accent: Accent = Accent.GREEN,
    val bgDim: Int = DEFAULT_DIM,            // backdrop dim, 0..100 %
    val bgTint: Tint = Tint.CHARCOAL,
    val forceStopOnKill: Boolean = true,
) {
    companion object { const val DEFAULT_DIM = 66 }   // design: scrim rgba(0,0,0,.66)
}

/** Stored keys. The names are the ones v1.0 wrote, so existing settings carry over. */
object SettingsKeys {
    const val SHOW_KILL_ALL = "show_kill_all"
    const val SIZE = "size"
    const val SHAPE = "shape"
    const val EXCEPTIONS = "exceptions"
    const val ACCENT = "accent"
    const val BG_DIM = "bg_dim_pct"
    const val BG_TINT = "bg_tint"
    const val FORCE_STOP = "force_stop_on_kill"
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
            bgTint = enumOr(get(SettingsKeys.BG_TINT), d.bgTint),
            forceStopOnKill = get(SettingsKeys.FORCE_STOP) as? Boolean ?: d.forceStopOnKill,
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
        SettingsKeys.BG_TINT to s.bgTint.name,
        SettingsKeys.FORCE_STOP to s.forceStopOnKill,
    )

    private inline fun <reified E : Enum<E>> enumOr(raw: Any?, default: E): E =
        (raw as? String)?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

    fun exceptionsSummary(n: Int): String =
        if (n <= 0) "No apps hidden" else "$n ${if (n == 1) "app" else "apps"} hidden"

    fun countLabel(n: Int): String =
        if (n <= 0) "" else "$n ${if (n == 1) "app" else "apps"}"
}
