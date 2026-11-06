package io.github.tomervancohen.rambleflow

import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** User-visible entry points all launch the same permission-aware recorder. */
internal object QuickRecordingAccess {
    fun pin(context: Context): Boolean {
        val shortcuts = context.getSystemService(ShortcutManager::class.java)
        if (!shortcuts.isRequestPinShortcutSupported) return false
        return shortcuts.requestPinShortcut(ShortcutInfo.Builder(context, "ramble-record")
            .setActivity(ComponentName(context, MainActivity::class.java))
            .setShortLabel(appText(R.string.ui_record_08a8c66)).setIcon(Icon.createWithResource(context, R.drawable.ic_record_tile))
            .setIntent(Intent(context, QuickRecordActivity::class.java).setAction(Intent.ACTION_MAIN)).build(), null)
    }
    fun addTile(context: Context, result: (String) -> Unit) {
        if (Build.VERSION.SDK_INT < 33) {
            result(appText(R.string.ui_open_quick_settings_edit_then_add_ramble_reco_7f34a85)); return
        }
        runCatching {
            context.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(context, QuickRecordTileService::class.java), "Ramble Record",
                Icon.createWithResource(context, R.drawable.ic_record_tile), context.mainExecutor,
            ) { code ->
                result(when (code) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED, StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> appText(R.string.ui_ramble_record_is_in_quick_settings_88f5794)
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_NOT_ADDED -> ""
                    else -> appText(R.string.ui_open_quick_settings_edit_then_add_ramble_reco_7f34a85)
                })
            }
        }.onFailure { result(appText(R.string.ui_add_ramble_record_by_editing_quick_settings_b11e890)) }
    }
}

class QuickRecordTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE // A launch action, not an on/off switch.
            label = "Ramble Record"
            subtitle = appText(R.string.ui_quick_recording_462793f)
            updateTile()
        }
    }
    // The PendingIntent overload was added in API 34. The Intent overload is only
    // reached on API 30–33, where it is still the supported platform API.
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    @Suppress("DEPRECATION")
    private fun launchLegacy(intent: Intent) { startActivityAndCollapse(intent) }

    override fun onClick() {
        super.onClick()
        // Start from a visible activity so microphone permission and FGS rules hold on all vendors.
        unlockAndRun {
            val launch = Intent(this, InternalRecordActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= 34) startActivityAndCollapse(PendingIntent.getActivity(this, 42, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            else launchLegacy(launch)
        }
    }
}
