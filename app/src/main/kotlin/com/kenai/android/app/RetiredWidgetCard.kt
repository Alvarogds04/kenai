package com.kenai.android.app

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.kenai.android.R
import com.kenai.android.character.BotState
import com.kenai.android.core.WidgetTone

/**
 * The card a retired Widget shows for one release (KN-13 L6, S3): Kenai resting and one plain line saying the Widget was retired and where
 * its function lives now, so a user who had it reads it on the home screen instead of losing it without a word. It draws nothing from the
 * engine and changes nothing: a tap opens the app. The receivers that show it stay declared until the next release, then they and the
 * layouts go.
 */
object RetiredWidgetCard {
    /** The card, its tap opening the app under [code] (one per retired Widget, so no two PendingIntents are the same). */
    fun views(context: Context, code: Int): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_retired)
        WidgetHub.paint(v, R.id.retired_root, R.id.retired_tint, WidgetTone.MUTED)
        v.setImageViewBitmap(R.id.retired_face, CharacterBitmap.of(context, BotState.SLEEPING, WidgetHub.px(context, 64), glow = true, tone = WidgetTone.MUTED))
        v.setContentDescription(R.id.retired_face, context.getString(R.string.widget_character_description, BotState.SLEEPING.key))
        v.setTextViewText(R.id.retired_line, context.getString(R.string.widget_retired_text))
        v.setViewVisibility(R.id.retired_line, View.VISIBLE)
        v.setOnClickPendingIntent(R.id.retired_root, WidgetHub.openApp(context, code))
        return v
    }
}
