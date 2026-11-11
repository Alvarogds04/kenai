package com.kenai.android.app

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kenai.android.R
import com.kenai.android.core.Glance
import com.kenai.android.core.LaunchScreen
import com.kenai.android.core.TilePlan
import com.kenai.android.core.TileTarget

/**
 * The quick-settings tile: on while the computer is connected; its second line says what waits ("2 waiting", or the most
 * urgent agent and its state, see [TilePlan]). A tap opens what waits (the request's sheet, the question's sheet) or Home,
 * through LaunchActivity (ours, not exported); the tile itself never settles a request. The system calls [onStartListening]
 * when the shade opens, so it needs no timer; [requestUpdate] asks it to refresh when the picture changes while the shade is open.
 */
class GlanceTile : TileService() {
    override fun onStartListening() = paint(this)

    // Below Android 14 only the Intent form exists; lint flags the call although it is behind the version check.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val model = (applicationContext as KenaiApp).model
        val open = Intent(this, LaunchActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .apply {
                when (val target = TilePlan.target(model.glance(), model.approvals, model.questions)) {
                    // Only the fingerprint: the sheet opens and the user chooses there, behind the screen lock.
                    is TileTarget.Approval -> putExtra(Notifications.EXTRA_FP, target.fingerprint)
                    is TileTarget.Question -> putExtra(LaunchActivity.EXTRA_QUESTION, target.pillId)
                    TileTarget.Home -> putExtra(LaunchActivity.EXTRA_SCREEN, LaunchScreen.HOME.key)
                }
            }
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(open)
        }
    }

    companion object {
        /** Asks the system to refresh the tile if it is on screen (it ignores this when the tile is not added). */
        fun requestUpdate(context: android.content.Context) {
            runCatching { requestListeningState(context, ComponentName(context, GlanceTile::class.java)) }
        }

        private fun paint(tile: TileService) {
            val t = tile.qsTile ?: return
            val g: Glance = (tile.applicationContext as KenaiApp).model.glance()
            t.label = "Kenai"
            // Over the lock screen it names no agent, like the notifications' public versions.
            t.subtitle = TilePlan.subtitle(g) { n -> tile.getString(R.string.tile_waiting, n) }
            t.state = if (g.connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            t.updateTile()
        }
    }
}
