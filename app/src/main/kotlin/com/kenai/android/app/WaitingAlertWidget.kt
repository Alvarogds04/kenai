package com.kenai.android.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.kenai.android.R
import com.kenai.android.core.LaunchScreen
import com.kenai.android.core.WidgetContent

/**
 * «Aprobaciones pendientes» (kenai-0.1 S9, screen 5). Calm when nothing waits for you: Koda resting, «Todo tranquilo» and «Nada te
 * necesita». When something waits and the card is wide enough (3x2 by default): Koda anxious, «2 te esperan» in orange over the computer's
 * name, one row per waiting agent (its own colour, its name, «Espera tu sí» or «Tiene una pregunta») and «Revisar el pedido», which opens
 * Home, where the first request is the hero. On a 2x2 card the count and the computer's name only. A tap anywhere opens the app. It only
 * opens things: a request waits for you in the app, behind the fingerprint.
 */
class WaitingAlertWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) =
        WidgetHub.refresh(context, manager, ids) { c, w -> views(c, w) }

    companion object {
        // Under 180dp wide (a 2x2) the centred card of before; from there the rows and the button. From 170dp high there is room for all three rows.
        private val MEDIUM_AT = SizeF(180f, 0f)
        private val LARGE_AT = SizeF(180f, 170f)

        private val ROWS = listOf(
            Row(R.id.alert_row0, R.id.alert_dot0, R.id.alert_name0, R.id.alert_state0),
            Row(R.id.alert_row1, R.id.alert_dot1, R.id.alert_name1, R.id.alert_state1),
            Row(R.id.alert_row2, R.id.alert_dot2, R.id.alert_name2, R.id.alert_state2),
        )

        private class Row(val row: Int, val dot: Int, val name: Int, val state: Int)

        fun views(context: Context, c: WidgetContent): RemoteViews =
            WidgetHub.responsive(MEDIUM_AT, LARGE_AT) { size -> render(context, c, size) }

        private fun render(context: Context, c: WidgetContent, size: WidgetHub.Size): RemoteViews {
            val v = RemoteViews(context.packageName, R.layout.widget_alert)
            WidgetHub.paint(v, R.id.alert_root, R.id.alert_tint, c.look)
            v.setOnClickPendingIntent(R.id.alert_root, WidgetHub.openApp(context, WidgetHub.CODE_ALERT))
            val waiting = c.waiting > 0
            val wide = waiting && size != WidgetHub.Size.SMALL
            v.setViewVisibility(R.id.alert_column, if (wide) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.alert_wide, if (wide) View.VISIBLE else View.GONE)
            if (wide) {
                v.setImageViewBitmap(R.id.alert_wide_face, CharacterBitmap.of(context, c.mood, WidgetHub.px(context, 44), glow = true, tone = c.look))
                v.setContentDescription(R.id.alert_wide_face, context.getString(R.string.widget_character_description, c.mood.key))
                v.setTextViewText(R.id.alert_wide_count, c.alertLine)
                v.setTextViewText(R.id.alert_wide_sub, c.alertSub)
                val rows = c.alertRows
                val shown = if (size == WidgetHub.Size.LARGE) ROWS.size else 2
                ROWS.forEachIndexed { i, r ->
                    val row = rows.getOrNull(i)
                    if (row == null || i >= shown) {
                        v.setViewVisibility(r.row, View.GONE)
                        return@forEachIndexed
                    }
                    v.setViewVisibility(r.row, View.VISIBLE)
                    v.setInt(r.dot, "setColorFilter", row.argb)
                    v.setTextViewText(r.name, row.name)
                    v.setTextViewText(r.state, row.word)
                }
                // «Revisar el pedido»: Home, whose hero is the oldest request; only a screen of the closed list opens.
                v.setOnClickPendingIntent(R.id.alert_review, WidgetHub.openScreen(context, WidgetHub.CODE_ALERT_BUTTON, LaunchScreen.HOME))
                v.setContentDescription(R.id.alert_review, context.getString(R.string.action_review))
                return v
            }
            v.setImageViewBitmap(R.id.alert_face, CharacterBitmap.of(context, c.mood, WidgetHub.px(context, 84), glow = true, tone = c.look))
            v.setContentDescription(R.id.alert_face, context.getString(R.string.widget_character_description, c.mood.key))
            v.setViewVisibility(R.id.alert_offline, if (c.connected) View.GONE else View.VISIBLE)
            v.setViewVisibility(R.id.alert_count, if (waiting) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.alert_calm, if (waiting) View.GONE else View.VISIBLE)
            v.setTextViewText(if (waiting) R.id.alert_count else R.id.alert_calm, c.alertLine)
            v.setTextViewText(R.id.alert_sub, c.alertSub)
            if (waiting) v.setContentDescription(R.id.alert_count, context.getString(R.string.widget_badge_description, c.waiting))
            return v
        }
    }
}
