package com.coucou.android.character

/** A colour with components 0..1. */
data class Rgb(val r: Double, val g: Double, val b: Double) {
    fun mix(o: Rgb, t: Double) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
}

/** An agent's state as the computer reports it (the `state` key on the wire). */
enum class BotState(val key: String) {
    IDLE("idle"), WORKING("working"), THINKING("thinking"), SEARCHING("searching"),
    APPROVAL("approval"), QUESTION("question"), ERROR("error"), FINISHED("finished"),
    RATELIMIT("ratelimit"), SLEEPING("sleeping"), DIZZY("dizzy"),
}

/** A short reaction of the character (a tap, a pet). */
enum class BotEmote(val key: String) {
    LOVE("love"), SURPRISED("surprised"), PROUD("proud"), WINK("wink"),
    YAWN("yawn"), HAPPY("happy"), ANNOYED("annoyed"),
}

/** How a state looks in the app's panels, widgets and on the character. */
data class StateStyle(val color: Rgb)

/** Plays a named sound; the app's player implements it. */
fun interface SoundSink { fun play(name: String) }

/** The app's state colours, shared by panels, widgets and the character, so they always agree. */
object StateColors {
    val C_IDLE = Rgb(0.902, 0.914, 0.933)
    val C_WORKING = Rgb(0.231, 0.62, 1.0)
    val C_THINKING = Rgb(0.545, 0.361, 0.965)
    val C_SEARCHING = Rgb(0.388, 0.396, 0.949)
    val C_APPROVAL = Rgb(0.961, 0.647, 0.141)
    val C_QUESTION = Rgb(0.133, 0.827, 0.933)
    val C_ERROR = Rgb(0.957, 0.314, 0.369)
    val C_FINISHED = Rgb(0.204, 0.831, 0.6)
    val C_RATELIMIT = Rgb(0.984, 0.573, 0.235)
    val C_SLEEPING = Rgb(0.58, 0.635, 0.722)
    val C_DIZZY = Rgb(0.957, 0.447, 0.714)

    val STATES: Map<BotState, StateStyle> = mapOf(
        BotState.IDLE to StateStyle(C_IDLE),
        BotState.WORKING to StateStyle(C_WORKING),
        BotState.THINKING to StateStyle(C_THINKING),
        BotState.SEARCHING to StateStyle(C_SEARCHING),
        BotState.APPROVAL to StateStyle(C_APPROVAL),
        BotState.QUESTION to StateStyle(C_QUESTION),
        BotState.ERROR to StateStyle(C_ERROR),
        BotState.FINISHED to StateStyle(C_FINISHED),
        BotState.RATELIMIT to StateStyle(C_RATELIMIT),
        BotState.SLEEPING to StateStyle(C_SLEEPING),
        BotState.DIZZY to StateStyle(C_DIZZY),
    )

    /** State to sound name played when an agent enters it. */
    val STATE_SOUND: Map<BotState, String> = mapOf(
        BotState.WORKING to "work", BotState.THINKING to "think", BotState.SEARCHING to "search",
        BotState.APPROVAL to "approval", BotState.QUESTION to "question", BotState.ERROR to "error",
        BotState.FINISHED to "finish", BotState.RATELIMIT to "rate", BotState.SLEEPING to "sleep",
        BotState.DIZZY to "dizzy",
    )
}
