package com.coucou.android.character

import kotlin.random.Random

/**
 * Placeholder Abito character (HU-049 T3): it keeps the state, the colour and the reactions the screens ask for, and
 * draws a plain breathing disc. Abito's real character replaces the drawing in T6; the API stays.
 *
 * [clock] gives milliseconds; [sound] hears reactions (a slap).
 */
class CharacterEngine(
    private val clock: () -> Double,
    @Suppress("unused") private val random: Random = Random.Default,
    private val sound: SoundSink? = null,
) {
    var state: BotState = BotState.IDLE
        private set

    /** The agent's colour; null uses the state colour. */
    var bodyColor: Rgb? = null

    /** A small character in a list row: no reactions drawn. */
    var isMini = false

    /** Called when three quick slaps make the character dizzy. */
    var onDizzy: (() -> Unit)? = null

    /** The reaction showing now and until when (ms), or a permanent one. */
    var emote: BotEmote? = null
        private set
    private var emoteUntil = 0.0
    private var permanentEmote: BotEmote? = null

    private val slaps = ArrayDeque<Double>()
    private var stateSince = clock()

    fun setState(s: BotState, force: Boolean = false) {
        if (s == state && !force) return
        state = s
        stateSince = clock()
    }

    fun setPermanentEmote(e: BotEmote?) { permanentEmote = e }

    fun triggerEmote(e: BotEmote, ms: Double = EMOTE_MS) {
        emote = e
        emoteUntil = clock() + ms
    }

    /** A tap. Three within [DIZZY_WINDOW_MS] make the character dizzy. */
    fun slap() {
        val now = clock()
        sound?.play("slap")
        triggerEmote(BotEmote.ANNOYED, SLAP_MS)
        slaps.addLast(now)
        while (slaps.isNotEmpty() && now - slaps.first() > DIZZY_WINDOW_MS) slaps.removeFirst()
        if (slaps.size >= DIZZY_SLAPS) {
            slaps.clear()
            onDizzy?.invoke()
        }
    }

    /** Advances time-based reactions; [dt] in seconds. */
    fun update(@Suppress("UNUSED_PARAMETER") dt: Double) {
        if (emote != null && clock() >= emoteUntil) emote = null
    }

    /** The reaction to draw now. */
    fun currentEmote(): BotEmote? = emote ?: permanentEmote

    /** The colour to draw now. */
    fun color(): Rgb = bodyColor ?: StateColors.STATES[state]?.color ?: StateColors.C_IDLE

    /** Breathing scale at time [t] seconds: slow when asleep, livelier when busy. */
    fun breath(t: Double): Double {
        val speed = when (state) {
            BotState.SLEEPING, BotState.IDLE -> 0.9
            BotState.APPROVAL, BotState.QUESTION, BotState.DIZZY -> 3.0
            else -> 1.8
        }
        return 1.0 + 0.035 * kotlin.math.sin(t * speed * Math.PI)
    }

    companion object {
        const val EMOTE_MS = 1_600.0
        const val SLAP_MS = 500.0
        const val DIZZY_WINDOW_MS = 1_200.0
        const val DIZZY_SLAPS = 3
    }
}
