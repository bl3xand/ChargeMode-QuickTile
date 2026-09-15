package io.github.bl3xand.chargecycle.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import io.github.bl3xand.chargecycle.R
import io.github.bl3xand.chargecycle.data.ChargeModeController
import io.github.bl3xand.chargecycle.data.CyclePrefs
import io.github.bl3xand.chargecycle.shizuku.ShizukuBridge
import io.github.bl3xand.chargecycle.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ChargeCycleTileService : TileService() {

    private var listeningScope: CoroutineScope? = null

    override fun onStartListening() {
        super.onStartListening()
        // Defensive: if the platform ever calls this again without an intervening
        // onStopListening(), don't orphan the previous scope's still-running collect.
        listeningScope?.cancel()
        val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
        listeningScope = scope
        scope.launch { refresh() }
        scope.launch {
            // Keeps the tile accurate if the mode changes some other way while the shade is open.
            ChargeModeController.observeChanges(applicationContext).collect { refresh() }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        listeningScope?.cancel()
        listeningScope = null
    }

    override fun onClick() {
        super.onClick()
        val ready = ChargeModeController.isSupportedDevice &&
            ChargeModeController.hasPermission(this) &&
            ShizukuBridge.hasPermission()
        if (!ready) {
            openAppForSetup()
            return
        }
        listeningScope?.launch {
            cycleToNextMode()
            refresh()
        }
    }

    private suspend fun cycleToNextMode() {
        val appContext = applicationContext
        val prefs = CyclePrefs(appContext)
        val enabled = prefs.enabledModes.sortedBy { it.ordinal }
        if (enabled.isEmpty()) {
            openAppForSetup()
            return
        }
        val current = ShizukuBridge.readCurrentMode() ?: prefs.lastAppliedMode
        val currentIndex = enabled.indexOf(current)
        val next = enabled[(currentIndex + 1) % enabled.size]
        if (ChargeModeController.apply(appContext, next)) {
            prefs.lastAppliedMode = next
        }
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val appContext = applicationContext
        val supported = ChargeModeController.isSupportedDevice
        val hasWritePermission = supported && ChargeModeController.hasPermission(appContext)
        val shizukuGranted = supported && ShizukuBridge.hasPermission()
        val ready = hasWritePermission && shizukuGranted
        val current = if (ready) ShizukuBridge.readCurrentMode() else null

        tile.icon = Icon.createWithResource(appContext, R.drawable.ic_tile_charge)
        tile.label = getString(R.string.app_name)
        tile.state = if (ready && current != null) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            !supported -> getString(R.string.tile_subtitle_unsupported)
            !hasWritePermission -> getString(R.string.tile_subtitle_no_permission)
            !shizukuGranted -> getString(R.string.tile_subtitle_shizuku_required)
            current != null -> getString(current.labelRes)
            else -> getString(R.string.tile_subtitle_setup)
        }
        tile.updateTile()
    }

    private fun openAppForSetup() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        startActivityAndCollapse(pendingIntent)
    }
}
