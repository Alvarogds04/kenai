package com.kenai.android.core

/**
 * One row of «Sesiones y actividad»: the dot's colour, the agent's name, its state word and, only when the engine sent a total, its own
 * progress counter («3/8»). Names, state words and colours only, like every Widget; nothing about the command, the file or the task.
 */
data class ActivityRow(val pillId: String, val name: String, val stateWord: String, val progress: String?, val tone: WidgetTone, val argb: Int)

/**
 * «Sesiones y actividad» (KN-13 S1-S4) as plain data, so the picture is a pure function of what the app knows and every rule is a unit
 * test: the headline and the badge of what the phone holds for you, the rows the size has room for (most urgent first, with the engine's
 * own progress when it sent one), the pending rows and their button, the finished-today line marked as approximate and counted on this phone,
 * and the one calm line when there is nothing to list. Nothing here is estimated: a row shows a count, never a rate (S4).
 */
data class ActivityPlan(
    /** The glance's headline, the same as on the notification, the tile and the first widget. */
    val headline: String,
    /** What the phone holds for you, from [WidgetContent.alertCount]; null when nothing waits, so no badge is drawn. */
    val badge: Int?,
    /** The card's paint, by the same rule as [WidgetContent.look]. */
    val tone: WidgetTone,
    /** Most urgent first, at most as many as the size has room for. */
    val rows: List<ActivityRow>,
    /** The pending rows the phone holds («Waiting for your OK», «Has a question»); empty when nothing waits. */
    val pending: List<AlertRow>,
    /** The pending section's button; null when nothing waits. */
    val button: String?,
    /** The finished-today count, said to be approximate and counted on this phone; null before anything finished. */
    val today: String?,
    /** The one calm line when there is no agent to list; null when there are rows. */
    val emptyLine: String?,
) {
    companion object {
        /** Rows on the smallest card the Widget is drawn at (about 2x2 of height). */
        const val ROWS_SMALL = 2

        /** Rows on a 3x2. */
        const val ROWS_MEDIUM = 3

        /** Rows on the 4x3 the Widget is placed at. */
        const val ROWS_LARGE = 5

        /**
         * The plan for [content] on a card that fits [rowLimit] rows. [w] picks the language of the lines the app builds itself, so a test
         * can pin both; it defaults to the phone's.
         */
        fun of(content: WidgetContent, rowLimit: Int, w: Words = Words.current()): ActivityPlan {
            val limit = rowLimit.coerceIn(0, ROWS_LARGE)
            val waiting = content.alertCount > 0
            return ActivityPlan(
                headline = content.headline,
                badge = content.alertCount.takeIf { waiting },
                tone = content.look,
                rows = content.tiles.take(limit).map { ActivityRow(it.pillId, it.name, it.stateWord, it.progress, it.tone, it.argb) },
                pending = if (waiting) content.alertRows.take(pendingRows(limit)) else emptyList(),
                button = if (waiting) content.pendingButton(w) else null,
                today = content.todayApproxLine(w),
                emptyLine = if (content.tiles.isEmpty()) content.emptyLine else null,
            )
        }

        /** The pending rows a size has room for beside its session rows: one on the smallest card, three on the 4x3. */
        private fun pendingRows(limit: Int): Int = when {
            limit >= ROWS_LARGE -> WidgetData.ALERT_ROWS
            limit >= ROWS_MEDIUM -> 2
            else -> 1
        }
    }
}
