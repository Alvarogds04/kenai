package com.kenai.android.character

/**
 * Koda, the cat (HU-055, design/hu-055-koda): pose rows of the 32x32 Chibi sprite sheet (res/drawable-nodpi/koda_chibi.png,
 * 4 columns x 7 rows) and the frame sequences the design catalogue defines. Pure, so it is unit-tested.
 */
enum class KodaPose(
    val row: Int,
    val fps: Int,
    val sequence: IntArray,
) {
    REPOSO(0, 8, intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 2, 2, 0, 0, 3, 0, 0, 1, 1, 1, 1)),
    ESCUCHA(1, 8, intArrayOf(0, 0, 0, 0, 1, 1, 1, 1, 0, 0, 0, 0, 0, 2, 2, 0, 0, 0, 0, 1, 1, 1, 3, 3, 0, 0)),
    PIENSA(2, 8, intArrayOf(0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0, 2, 2, 3, 3, 3, 3, 1, 1, 1, 1, 0, 0, 0)),
    TRABAJA(3, 8, intArrayOf(0, 0, 1, 1, 2, 2, 3, 3)),
    ESPERA(4, 8, intArrayOf(0, 0, 0, 1, 2, 2, 3, 3, 0, 1, 2, 3, 3, 0, 0, 0)),
    TERMINO(5, 8, intArrayOf(0, 0, 0, 0, 0, 0, 1, 1, 2, 2, 2, 2, 3, 3, 0, 0, 0, 0, 0, 0)),
    ERROR(6, 8, intArrayOf(0, 0, 0, 0, 0, 1, 1, 2, 2, 3, 3, 2, 3, 2, 3, 0, 0, 0, 0, 1, 1)),
    ;

    /** The sheet column to draw [seconds] after the pose started; the first frame only when motion is reduced. */
    fun frameAt(
        seconds: Double,
        reduceMotion: Boolean = false,
    ): Int {
        if (reduceMotion) return sequence[0]
        val tick = (seconds * fps).toLong().coerceAtLeast(0)
        return sequence[(tick % sequence.size).toInt()]
    }

    companion object {
        fun of(state: BotState): KodaPose =
            when (state) {
                BotState.IDLE, BotState.SLEEPING -> REPOSO
                BotState.THINKING, BotState.SEARCHING -> PIENSA
                BotState.WORKING -> TRABAJA
                BotState.APPROVAL, BotState.QUESTION, BotState.RATELIMIT -> ESPERA
                BotState.FINISHED -> TERMINO
                BotState.ERROR, BotState.DIZZY -> ERROR
            }

        /**
         * The nearest pose for a short reaction (S5): a pet or a wink is the attentive, content cat; happy and proud the done
         * pose; surprised the "?" pose; a yawn asleep; a slap (annoyed) the flat-eared error pose.
         */
        fun of(emote: BotEmote): KodaPose =
            when (emote) {
                BotEmote.LOVE, BotEmote.WINK -> ESCUCHA
                BotEmote.HAPPY, BotEmote.PROUD -> TERMINO
                BotEmote.SURPRISED -> ESPERA
                BotEmote.YAWN -> REPOSO
                BotEmote.ANNOYED -> ERROR
            }
    }
}

/** The role colours of the desktop design system (design/system.md), never themed: they carry the state. */
object KodaRole {
    const val GO = 0xFFB8FF2EL
    const val SUN = 0xFFFFE100L
    const val LIVE = 0xFF2EB4FFL
    const val RED = 0xFFFF5C75L
    const val FAINT = 0xFF6F6D66L

    /** The ink that sits on a role-colour fill. */
    const val ON_ROLE = 0xFF0B0C0EL

    fun of(state: BotState): Long =
        when (state) {
            BotState.APPROVAL, BotState.QUESTION, BotState.RATELIMIT -> SUN
            BotState.WORKING, BotState.THINKING, BotState.SEARCHING -> LIVE
            BotState.FINISHED -> GO
            BotState.ERROR, BotState.DIZZY -> RED
            BotState.IDLE, BotState.SLEEPING -> FAINT
        }
}
