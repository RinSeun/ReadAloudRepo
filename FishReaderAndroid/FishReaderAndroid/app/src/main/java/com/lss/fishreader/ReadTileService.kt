package com.lss.fishreader

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.lss.fishreader.FishPlayer.Phase

/**
 * "Fish Read" tile in the swipe-down Quick Settings panel.
 * Tap: reads whatever you copied. Tap while reading: stops.
 */
class ReadTileService : TileService() {

    private fun isBusy(): Boolean {
        val p = FishPlayer.shared.snapshot().phase
        return p == Phase.LOADING || p == Phase.PLAYING || p == Phase.PAUSED
    }

    override fun onStartListening() {
        super.onStartListening()
        val tile = qsTile ?: return
        val busy = isBusy()
        tile.state = if (busy) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Fish Read"
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = if (busy) "Tap to stop" else "Reads copied text"
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        if (isBusy()) {
            FishPlayer.shared.stop()
            onStartListening()
            return
        }
        val intent = Intent(this, ClipReadActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
