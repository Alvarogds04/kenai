package com.kenai.android.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * RETIRED in KN-13 (L5, L6): a 1x1 pose carried no information of its own, and Kenai's face is now on «Sesiones y actividad» and on «At a
 * glance». The receiver and its provider stay declared for one release so an instance already on a home screen is not dropped without a
 * word: a placed one shows the small retired card saying where the face went, and opens the app. It can be deleted, with its provider file
 * and that card, in the next release.
 */
class MoodWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // One small card for every placed instance, whatever the app knows; nothing here throws out of a broadcast.
        runCatching { WidgetHub.refresh(context, manager, ids) { c, w -> views(c) } }
    }

    companion object {
        fun views(context: Context): RemoteViews = RetiredWidgetCard.views(context, WidgetHub.CODE_RETIRED_MOOD)
    }
}
