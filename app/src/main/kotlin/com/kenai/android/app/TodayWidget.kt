package com.kenai.android.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * RETIRED in KN-13 (L5, L6): a count kept on this phone only, presented as a day's metric, was an estimate shown as if it were the
 * engine's (S2, S4). The count is not lost: «Sesiones y actividad» shows it as one line that says it is approximate and counted on this
 * phone, and the alarm that starts the day again at local midnight now belongs to that Widget ([GlanceWidget]).
 *
 * The receiver and its provider stay declared for one release so an instance already on a home screen is not dropped without a word: a
 * placed one shows the small retired card, which says where the count went and opens the app. It can be deleted, with its provider file and
 * that card, in the next release.
 */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // One small card for every placed instance, whatever the app knows; nothing here throws out of a broadcast.
        runCatching { WidgetHub.refresh(context, manager, ids) { c, _ -> views(c) } }
    }

    /**
     * An alarm armed by the previous version still targets this receiver. Its midnight is handed on to «Sesiones y actividad», which redraws
     * and arms its own alarm from then on (KN-13 review). An explicit component, never an implicit broadcast; nothing throws out of here.
     */
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == WidgetHub.ACTION_MIDNIGHT) {
            runCatching { context.sendBroadcast(Intent(WidgetHub.ACTION_MIDNIGHT).setClass(context, GlanceWidget::class.java)) }
        }
    }

    companion object {
        fun views(context: Context): RemoteViews = RetiredWidgetCard.views(context, WidgetHub.CODE_RETIRED_TODAY)
    }
}
