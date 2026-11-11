package com.kenai.android.character

/**
 * One Kenai character on screen: Koda the cat (S5), behind the API the screens have always used. Screens set the agent's
 * [state]; a tap is a [slap] (three quick ones make it dizzy), a long press an emote. Koda has no clay body to knead: each
 * state and each emote is a pose row of the sprite sheet ([KodaPose]), so the engine keeps only the time, the pose showing now
 * and when it started. [CharacterView] draws it.
 *
 * [clock] gives milliseconds and only times the slaps; animation time is what [update] adds up, so a paused screen does not
 * jump.
 */
class CharacterEngine(
    private val clock: () -> Double,
    private val sound: SoundSink? = null,
) {
    var state: BotState = BotState.IDLE
        private set

    /** The agent's colour: a soft light behind Koda. Null draws none. */
    var bodyColor: Rgb? = null

    /** A small character in a list row: no light behind it. */
    var isMini = false

    /** Called when three quick slaps make the character dizzy. */
    var onDizzy: (() -> Unit)? = null

    private class Reaction(
        val emote: BotEmote,
        val t0: Double,
    )

    private var t = 0.0
    private var stateT0 = 0.0
    private var reaction: Reaction? = null
    private var permanentEmote: BotEmote? = null
    private val slaps = ArrayDeque<Double>()

    fun setState(
        s: BotState,
        force: Boolean = false,
    ) {
        if (s == state && !force) return
        if (s != state) {
            reaction = null
            stateT0 = t
        }
        state = s
    }

    /** An emote that never ends (the gallery shows each one). */
    fun setPermanentEmote(e: BotEmote?) {
        permanentEmote = e
    }

    /** Plays an emote once over the current pose; one at a time, never queued. */
    fun triggerEmote(e: BotEmote): Boolean {
        if (reaction != null) return false
        reaction = Reaction(e, t)
        return true
    }

    /** The emote showing now, if any. */
    fun currentEmote(): BotEmote? = reaction?.emote ?: permanentEmote

    /** A tap: annoyed for a moment. Three within [DIZZY_WINDOW_MS] make the character dizzy ([onDizzy]). */
    fun slap() {
        val now = clock()
        sound?.play("slap")
        reaction = null
        triggerEmote(BotEmote.ANNOYED)
        slaps.addLast(now)
        while (slaps.isNotEmpty() && now - slaps.first() > DIZZY_WINDOW_MS) slaps.removeFirst()
        if (slaps.size >= DIZZY_SLAPS) {
            slaps.clear()
            onDizzy?.invoke()
        }
    }

    /** The character's colour now: the agent's, else the role colour of its state (design/system.md). */
    fun color(): Rgb = bodyColor ?: KodaRole.of(state).let { Rgb(((it shr 16) and 0xFF) / 255.0, ((it shr 8) and 0xFF) / 255.0, (it and 0xFF) / 255.0) }

    /** Advances the animation by [dt] seconds (capped, so a paused screen does not jump). */
    fun update(dt: Double) {
        t += dt.coerceIn(0.0, MAX_STEP_S)
        reaction?.let { if (t - it.t0 >= EMOTE_SECONDS.getValue(it.emote)) reaction = null }
    }

    /** The sheet row to show now: the emote's nearest pose while one plays, else the state's. */
    fun pose(): KodaPose = currentEmote()?.let { KodaPose.of(it) } ?: KodaPose.of(state)

    /** The sheet column to show now; the pose's first frame when motion is reduced. */
    fun frame(reduceMotion: Boolean = false): Int = pose().frameAt(t - (reaction?.t0 ?: stateT0), reduceMotion)

    companion object {
        const val DIZZY_WINDOW_MS = 1_200.0
        const val DIZZY_SLAPS = 3
        private const val MAX_STEP_S = 1.0 / 20

        /** How long each reaction shows, in seconds: the desktop's own emote lengths. */
        private val EMOTE_SECONDS =
            mapOf(
                BotEmote.LOVE to 1.8,
                BotEmote.SURPRISED to 1.0,
                BotEmote.PROUD to 1.3,
                BotEmote.WINK to .8,
                BotEmote.YAWN to 1.0,
                BotEmote.HAPPY to 1.0,
                BotEmote.ANNOYED to .8,
            )
    }
}
