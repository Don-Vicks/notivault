package com.notivault.capture

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.notivault.data.prefs.SettingsStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Quick Settings tile: pause/resume recording without opening the app. */
@AndroidEntryPoint
class PauseTileService : TileService() {
    @Inject lateinit var settings: SettingsStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() { scope.launch { render(withContext(Dispatchers.IO) { settings.current().capturePaused }) } }

    override fun onClick() {
        scope.launch {
            val paused = !withContext(Dispatchers.IO) { settings.current().capturePaused }
            withContext(Dispatchers.IO) { settings.setCapturePaused(paused) }
            render(paused)
        }
    }

    private fun render(paused: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = "NotiVault"
        if (android.os.Build.VERSION.SDK_INT >= 29) tile.subtitle = if (paused) "Paused" else "Recording"
        tile.updateTile()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
}
