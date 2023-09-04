package com.thewizrd.simplewear.wearable.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmapOrNull
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.TileLayoutPreview
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
import com.thewizrd.simplewear.R

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun DashboardTilePreview() {
    val context = LocalContext.current
    val state = remember {
        DashboardTileState(
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
        )
    }
    val renderer = remember {
        DashboardTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun DashboardLoadingTilePreview() {
    val context = LocalContext.current
    val state = remember {
        DashboardTileState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            batteryStatus = null,
            actions = emptyMap()
        )
    }
    val renderer = remember {
        DashboardTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DashboardDisconnectTilePreview() {
    val context = LocalContext.current
    val state = remember {
        DashboardTileState(
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
        )
    }
    val renderer = remember {
        DashboardTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
fun DashboardNotInstalledTilePreview() {
    val context = LocalContext.current
    val state = remember {
        DashboardTileState(
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
        )
    }
    val renderer = remember {
        DashboardTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = Unit,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun MediaPlayerTilePreview() {
    val context = LocalContext.current
    val state = remember {
        MediaPlayerTileState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            title = "Title",
            artist = "Artist",
            playbackState = PlaybackState.PAUSED,
            audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
            artwork = ContextCompat.getDrawable(context, R.drawable.ws_full_sad)?.toBitmapOrNull()
        )
    }
    val renderer = remember {
        MediaPlayerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = state,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun MediaPlayerEmptyTilePreview() {
    val context = LocalContext.current
    val state = remember {
        MediaPlayerTileState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            title = null,
            artist = null,
            playbackState = null,
            audioStreamState = null,
            artwork = null
        )
    }
    val renderer = remember {
        MediaPlayerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = state,
        renderer = renderer
    )
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@Composable
fun MediaPlayerNotPlayingTilePreview() {
    val context = LocalContext.current
    val state = remember {
        MediaPlayerTileState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            title = null,
            artist = null,
            playbackState = PlaybackState.NONE,
            audioStreamState = AudioStreamState(3, 0, 5, AudioStreamType.MUSIC),
            artwork = null
        )
    }
    val renderer = remember {
        MediaPlayerTileRenderer(context, debugResourceMode = true)
    }

    TileLayoutPreview(
        state = state,
        resourceState = state,
        renderer = renderer
    )
}