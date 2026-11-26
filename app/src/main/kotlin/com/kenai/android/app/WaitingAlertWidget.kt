package com.kenai.android.app

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews
import com.kenai.android.core.WidgetContent

/**
 * RETIRED in KN-13 (L5, L6): what waits for you is now the pending section of «Sesiones y actividad» ([GlanceWidget]), with the same rows
 * (one per request or question the phone holds, in its agent's own colour, with «Espera tu sí» or «Tiene una pregunta») and the same button
 * that opens Home. The receiver and its provider stay declared for one release so an instance already on a home screen is not dropped
 * without a word; a placed one renders the new card, and the picker hides it from Android 12 on and shows it named "(retired)" before that.
 * It can be deleted, with its provider file, in the next release.
 */
class WaitingAlertWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Whatever state the app is in, the new card is drawn; nothing here throws out of a broadcast.
        runCatching { WidgetHub.refresh(context, manager, ids) { c, w -> views(c, w) } }
    }

    companion object {
        /** The same picture «Sesiones y actividad» draws: its pending rows and its button are the alert's. */
        fun views(context: Context, c: WidgetContent): RemoteViews = GlanceWidget.views(context, c)
    }
}
