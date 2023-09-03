package com.thewizrd.simplewear.wearable.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.TileLayoutPreview
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus

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