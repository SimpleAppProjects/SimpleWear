@file:OptIn(ExperimentalHorologistApi::class)
@file:Suppress("FunctionName")

package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.wear.tiles.tooling.preview.TilePreviewData
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.tileRendererPreviewData
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.tools.WearTilePreviewDevices
import kotlinx.coroutines.runBlocking

@WearTilePreviewDevices
private fun DashboardTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)

@WearTilePreviewDevices
private fun DashboardLoadingTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        batteryStatus = null,
        actions = emptyMap()
    ),
    resourceState = Unit
)

@WearTilePreviewDevices
private fun DashboardDisconnectTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)

@WearTilePreviewDevices
private fun DashboardNotInstalledTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.APPNOTINSTALLED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)

@WearTilePreviewDevices
private fun MediaPlayerTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = "Title",
        artist = "Artist",
        playbackState = PlaybackState.PAUSED,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        positionState = PositionState(100, 50),
        artwork = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ws_full_sad)?.toBitmapOrNull()
                ?.toByteArray()
        },
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )

    return tileRendererPreviewData(
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearTilePreviewDevices
private fun MediaPlayerEmptyTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = null,
        artist = null,
        playbackState = null,
        audioStreamState = null,
        artwork = null
    )

    return tileRendererPreviewData(
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state
    )
}

@WearTilePreviewDevices
private fun MediaPlayerNotPlayingTilePreview(context: Context): TilePreviewData {
    val state = MediaPlayerTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        title = null,
        artist = null,
        playbackState = PlaybackState.NONE,
        audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
        artwork = null,
        appIcon = runBlocking {
            ContextCompat.getDrawable(context, R.drawable.ic_play_circle_simpleblue)
                ?.toBitmapOrNull()
                ?.toByteArray()
        }
    )

    return tileRendererPreviewData(
        renderer = MediaPlayerTileRenderer(context, debugResourceMode = true),
        tileState = state,
        resourceState = state,
    )
}