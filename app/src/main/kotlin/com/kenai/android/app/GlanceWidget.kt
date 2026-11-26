package com.kenai.android.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.kenai.android.R
import com.kenai.android.core.ActivityPlan
import com.kenai.android.core.LaunchScreen
import com.kenai.android.core.WidgetContent

/**
 * «Sesiones y actividad» (KN-13 S1-S4), the redesign of the old summary widget in place: the same receiver and the same provider, so the
 * instances already on a home screen are kept and simply start showing the new card.
 *
 * On the shared card: a header with Kenai, the headline and the badge counting what the phone holds for you; one row per agent (a dot in
 * the state's tone, its name, its state word and, only when the engine sent a total, its own «3/8» progress); the pending section, with one
 * row per request or question the phone holds and the button that opens Home; the count of tasks finished today, said to be approximate
 * and counted on this phone; and one calm line when there is no agent to list. How many rows show follows the room the launcher gives it
 * ([ActivityPlan]); nothing is estimated from the engine's progress (S4), and no row carries the command, the file or the task.
 *
 * A tap on a row opens that agent; a tap anywhere else opens the app. It only opens things: a request waits for you in the app, behind the
 * fingerprint. Nothing updates on a timer (updatePeriodMillis = 0): the app pushes a new picture when something changes, and this Widget
 * keeps the one inexact alarm that redraws it at the next local midnight, so the finished-today line starts again at 0 (KN-13 L6).
 */
class GlanceWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // A retired shell may be the only one left: drawing never throws out of here, and the alarm is set after it either way.
        runCatching { WidgetHub.refresh(context, manager, ids) { c, w -> views(c, w) } }
        runCatching { WidgetHub.scheduleMidnight(context) }
    }

    override fun onDisabled(context: Context) = WidgetHub.cancelMidnight(context)

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        // The day may be another one: either midnight itself, or the clock or the time zone being changed.
        if (intent.action in DAY_CHANGED) {
            runCatching { WidgetHub.refreshAll(context) }
            runCatching { WidgetHub.scheduleMidnight(context) }
        }
    }

    companion object {
        // Midnight (our alarm), and the clock or the time zone being changed: the finished-today line may belong to another day.
        private val DAY_CHANGED = setOf(WidgetHub.ACTION_MIDNIGHT, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED)

        // Header 32dp + padding 20dp + 48dp per row: two rows fit 144dp, three 192dp, five 288dp.
        private val MEDIUM_AT = SizeF(0f, 192f)
        private val LARGE_AT = SizeF(0f, 290f)

        private class Row(val row: Int, val dot: Int, val name: Int, val state: Int, val progress: Int)

        private val ROWS = listOf(
            Row(R.id.glance_row0, R.id.glance_dot0, R.id.glance_name0, R.id.glance_state0, R.id.glance_progress0),
            Row(R.id.glance_row1, R.id.glance_dot1, R.id.glance_name1, R.id.glance_state1, R.id.glance_progress1),
            Row(R.id.glance_row2, R.id.glance_dot2, R.id.glance_name2, R.id.glance_state2, R.id.glance_progress2),
            Row(R.id.glance_row3, R.id.glance_dot3, R.id.glance_name3, R.id.glance_state3, R.id.glance_progress3),
            Row(R.id.glance_row4, R.id.glance_dot4, R.id.glance_name4, R.id.glance_state4, R.id.glance_progress4),
        )

        private class Pending(val row: Int, val dot: Int, val name: Int, val word: Int)

        private val PENDING = listOf(
            Pending(R.id.glance_pending_row0, R.id.glance_pending_dot0, R.id.glance_pending_name0, R.id.glance_pending_word0),
            Pending(R.id.glance_pending_row1, R.id.glance_pending_dot1, R.id.glance_pending_name1, R.id.glance_pending_word1),
            Pending(R.id.glance_pending_row2, R.id.glance_pending_dot2, R.id.glance_pending_name2, R.id.glance_pending_word2),
        )

        /** The picture for [c]: the rows and the pending section the size has room for, and one card for all of it. */
        fun views(context: Context, c: WidgetContent): RemoteViews =
            WidgetHub.responsive(MEDIUM_AT, LARGE_AT) { size -> render(context, c, size) }

        private fun limitOf(size: WidgetHub.Size): Int = when (size) {
            WidgetHub.Size.SMALL -> ActivityPlan.ROWS_SMALL
            WidgetHub.Size.MEDIUM -> ActivityPlan.ROWS_MEDIUM
            WidgetHub.Size.LARGE -> ActivityPlan.ROWS_LARGE
        }

        private fun render(context: Context, c: WidgetContent, size: WidgetHub.Size): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_glance)
            val plan = ActivityPlan.of(c, limitOf(size))
            WidgetHub.paint(v, R.id.widget_root, R.id.glance_tint, plan.tone)
            // A tap anywhere opens the app; a row is the only thing that opens an agent.
            v.setOnClickPendingIntent(R.id.widget_root, WidgetHub.openApp(context, WidgetHub.CODE_GLANCE))
            val empty = plan.rows.isEmpty()
            v.setViewVisibility(R.id.glance_header, if (empty) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.glance_rows, if (empty) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.glance_empty, if (empty) View.VISIBLE else View.GONE)

            if (empty) {
                // Nothing to list: Kenai with his glow and the one clear line saying why, as on the List widget before it.
                v.setImageViewBitmap(R.id.glance_empty_face, CharacterBitmap.of(context, c.mood, WidgetHub.px(context, 96), glow = true, tone = plan.tone))
                v.setContentDescription(R.id.glance_empty_face, context.getString(R.string.widget_character_description, c.mood.key))
                v.setImageViewResource(R.id.glance_empty_icon, if (c.connected) R.drawable.widget_dot else R.drawable.ic_widget_offline)
                v.setInt(R.id.glance_empty_icon, "setColorFilter", plan.tone.argb)
                v.setTextViewText(R.id.glance_empty_title, plan.headline)
                v.setTextViewText(R.id.glance_empty_line, plan.emptyLine.orEmpty())
            } else {
                v.setImageViewBitmap(R.id.glance_character, CharacterBitmap.of(context, c.mood, WidgetHub.px(context, 32), glow = true, tone = plan.tone))
                v.setContentDescription(R.id.glance_character, context.getString(R.string.widget_character_description, c.mood.key))
                v.setViewVisibility(R.id.glance_offline, if (c.connected) View.GONE else View.VISIBLE)
                v.setTextViewText(R.id.glance_headline, plan.headline)
                val badge = plan.badge
                if (badge != null) {
                    v.setTextViewText(R.id.glance_badge, badge.toString())
                    v.setContentDescription(R.id.glance_badge, context.getString(R.string.widget_badge_description, badge))
                    v.setViewVisibility(R.id.glance_badge, View.VISIBLE)
                } else {
                    v.setViewVisibility(R.id.glance_badge, View.GONE)
                }
                ROWS.forEachIndexed { i, r ->
                    val row = plan.rows.getOrNull(i)
                    if (row == null) {
                        v.setViewVisibility(r.row, View.GONE)
                        return@forEachIndexed
                    }
                    v.setViewVisibility(r.row, View.VISIBLE)
                    v.setInt(r.dot, "setColorFilter", row.argb)
                    v.setTextViewText(r.name, row.name)
                    v.setTextViewText(r.state, row.stateWord)
                    // The engine's own counter, only when it sent a total: never a rate, never an estimate (S4).
                    val progress = row.progress
                    if (progress != null) {
                        v.setTextViewText(r.progress, progress)
                        v.setViewVisibility(r.progress, View.VISIBLE)
                    } else {
                        v.setViewVisibility(r.progress, View.GONE)
                    }
                    v.setOnClickPendingIntent(r.row, WidgetHub.openSession(context, WidgetHub.CODE_GLANCE_ROW + i, row.pillId))
                }
            }

            // The pending section: what this phone holds for you, and the button that opens Home, where it waits behind the fingerprint.
            val waiting = plan.pending.isNotEmpty()
            v.setViewVisibility(R.id.glance_pending, if (waiting) View.VISIBLE else View.GONE)
            PENDING.forEachIndexed { i, p ->
                val row = plan.pending.getOrNull(i)
                if (row == null) {
                    v.setViewVisibility(p.row, View.GONE)
                    return@forEachIndexed
                }
                v.setViewVisibility(p.row, View.VISIBLE)
                v.setInt(p.dot, "setColorFilter", row.argb)
                v.setTextViewText(p.name, row.name)
                v.setTextViewText(p.word, row.word)
            }
            val button = plan.button
            if (button != null) {
                v.setTextViewText(R.id.glance_review, button)
                v.setContentDescription(R.id.glance_review, button)
                v.setOnClickPendingIntent(R.id.glance_review, WidgetHub.openScreen(context, WidgetHub.CODE_GLANCE_BUTTON, LaunchScreen.HOME))
                v.setViewVisibility(R.id.glance_review, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.glance_review, View.GONE)
            }

            // What only this phone counted, and it says so.
            val today = plan.today
            if (today != null) {
                v.setTextViewText(R.id.glance_today, today)
                v.setViewVisibility(R.id.glance_today, View.VISIBLE)
            } else {
                v.setViewVisibility(R.id.glance_today, View.GONE)
            }
            return v
        }
    }
}
