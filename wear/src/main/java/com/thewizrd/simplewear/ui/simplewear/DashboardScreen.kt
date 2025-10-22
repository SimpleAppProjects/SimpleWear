package com.thewizrd.simplewear.ui.simplewear

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.contentColorFor
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ContextUtils.isSmallestWidth
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.onClick
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.components.WearDivider
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.fillDashboard
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.DashboardState
import com.thewizrd.simplewear.viewmodels.DashboardViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    dashboardViewModel: DashboardViewModel,
    scrollState: ScrollState = rememberScrollState(),
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var refreshing by remember { mutableStateOf(false) }

    val uiState by dashboardViewModel.uiState.collectAsState()

    DashboardScreen(
        modifier = modifier,
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            lifecycleOwner.lifecycleScope.launch {
                dashboardViewModel.requestUpdate()
                delay(1000)
                refreshing = false
            }
        },
        dashboardState = uiState,
        scrollState = scrollState,
        onActionClicked = { model ->
            AnalyticsLogger.logEvent("dash_action_clicked", Bundle().apply {
                putString("action", model.action.actionType.name)
            })

            model.onClick(
                navController,
                onActionChanged = {
                    dashboardViewModel.requestActionChange(it)
                },
                onActionStatus = {
                    dashboardViewModel.requestActionStatusUpdate(it)
                }
            )
        },
        onDashSettingsClick = {
            navController.navigate(
                Screen.DashboardConfig.route,
                navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
            )
        },
        onDashTileSettingsClick = {
            navController.navigate(
                Screen.DashboardTileConfig.route,
                navOptions = NavOptions.Builder()
                    .setLaunchSingleTop(true)
                    .build()
            )
        }
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    dashboardState: DashboardState,
    scrollState: ScrollState = rememberScrollState(),
    onActionClicked: (ActionButtonViewModel) -> Unit = {},
    onDashSettingsClick: () -> Unit = {},
    onDashTileSettingsClick: () -> Unit = {},
) {
    val isPreview = LocalInspectionMode.current

    val pullRefreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        modifier = modifier.fillMaxSize(),
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        indicator = {
            PullToRefreshDefaults.LoadingIndicator(
                modifier = Modifier.align(Alignment.TopCenter),
                state = pullRefreshState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .requestFocusOnHierarchyActive()
                .rotaryScrollable(
                    focusRequester = rememberFocusRequester(),
                    behavior = RotaryScrollableDefaults.behavior(scrollState)
                ),
        ) {
            if (isPreview) {
                TimeText()
            }

            Column(
                modifier = Modifier.fillDashboard(),
            ) {
                // Device state
                DeviceStateChip(
                    isStatusLoading = dashboardState.isStatusLoading,
                    connectionStatus = dashboardState.connectionStatus
                )
                // Battery Status
                if (dashboardState.showBatteryState) {
                    BatteryStatusChip(
                        isStatusLoading = dashboardState.isStatusLoading,
                        batteryStatus = dashboardState.batteryStatus
                    )
                }
                // Actions list
                ActionsList(
                    dashboardState.actions,
                    isGridLayout = dashboardState.isGridLayout,
                    isActionsClickable = dashboardState.isActionsClickable,
                    onActionClicked = onActionClicked
                )
                // Settings
                WearDivider(modifier = Modifier.padding(vertical = 8.dp))
                DashboardSettings(
                    dashboardState,
                    scrollState,
                    onDashSettingsClick,
                    onDashTileSettingsClick
                )
            }
        }
    }
}

@Composable
private fun DeviceStateChip(
    isStatusLoading: Boolean = false,
    connectionStatus: WearConnectionStatus? = null
) {
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_smartphone_white_24dp),
                contentDescription = stringResource(R.string.desc_phone_state)
            )
        },
        label = {
            Text(
                text = if (isStatusLoading) {
                    stringResource(id = R.string.message_gettingstatus)
                } else {
                    when (connectionStatus) {
                        WearConnectionStatus.DISCONNECTED -> {
                            stringResource(id = R.string.status_disconnected)
                        }

                        WearConnectionStatus.CONNECTING -> {
                            stringResource(id = R.string.status_connecting)
                        }

                        WearConnectionStatus.APPNOTINSTALLED -> {
                            stringResource(id = R.string.error_notinstalled)
                        }

                        WearConnectionStatus.CONNECTED -> {
                            stringResource(id = R.string.status_connected)
                        }

                        null -> {
                            stringResource(id = R.string.message_gettingstatus)
                        }
                    }
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        onClick = {},
        enabled = false,
        colors = transparentButtonColors(),
        border = null
    )
}

@Composable
private fun BatteryStatusChip(
    isStatusLoading: Boolean = false,
    batteryStatus: BatteryStatus? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        OutlinedButton(
            modifier = Modifier.weight(1f, fill = true),
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.ic_battery_std_white_24dp),
                    contentDescription = stringResource(R.string.title_batt_state)
                )
            },
            label = {
                Text(
                    text = batteryStatus?.let {
                        "${it.batteryLevel}%"
                    } ?: stringResource(id = R.string.state_syncing),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            secondaryLabel = batteryStatus?.let {
                {
                    Text(
                        text = if (it.isCharging) {
                            stringResource(id = R.string.batt_state_charging)
                        } else {
                            stringResource(id = R.string.batt_state_discharging)
                        },
                        maxLines = 1
                    )
                }
            },
            onClick = {},
            enabled = false,
            colors = transparentButtonColors(),
            border = null
        )
        if (isStatusLoading) {
            Box(
                modifier = Modifier
                    .width(52.dp)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun ActionsList(
    actions: List<ActionButtonViewModel>,
    isGridLayout: Boolean = true,
    isActionsClickable: Boolean = true,
    onActionClicked: (ActionButtonViewModel) -> Unit
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current

    // Title
    Text(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .heightIn(min = 48.dp)
            .wrapContentHeight(align = Alignment.CenterVertically),
        text = stringResource(id = R.string.title_actions),
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 1
    )
    if (isGridLayout) {
        val buttonSize by remember {
            derivedStateOf {
                if ((context.isSmallestWidth(180) && !config.isScreenRound) || (context.isSmallestWidth(
                        210
                    ) && config.isScreenRound)
                ) {
                    48.dp
                } else {
                    40.dp
                }
            }
        }
        var gridSize by remember { mutableStateOf(Size.Zero) }
        val horizPadding by remember(gridSize) {
            val colWidth = gridSize.width / 3f
            mutableFloatStateOf((colWidth - context.dpToPx(buttonSize.value)) / 2)
        }

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .onGloballyPositioned {
                    gridSize = it.size.toSize()
                },
            maxItemsInEachRow = 3,
            horizontalArrangement = Arrangement.spacedBy(
                horizPadding.dp,
                Alignment.CenterHorizontally
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions.forEach {
                ActionGridButton(
                    modifier = Modifier.requiredSize(buttonSize),
                    iconSize = buttonSize / 2,
                    model = it,
                    isClickable = isActionsClickable,
                    onClick = onActionClicked
                )
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            actions.forEach {
                ActionListButton(
                    model = it,
                    isClickable = isActionsClickable,
                    onClick = onActionClicked
                )
            }
        }
    }
}

@Composable
private fun ActionGridButton(
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    model: ActionButtonViewModel,
    isClickable: Boolean = true,
    onClick: (ActionButtonViewModel) -> Unit
) {
    val context = LocalContext.current
    val interactionSource = remember { MutableInteractionSource() }
    val viewConfig = LocalViewConfiguration.current

    Button(
        modifier = modifier,
        interactionSource = interactionSource,
        enabled = model.buttonState != null,
        colors = model.buttonState?.let { state ->
            actionButtonColors(state)
        } ?: run {
            // Indeterminate state
            indeterminateActionButtonColors()
        },
        onClick = {
            if (isClickable && model.getItemViewType() != ActionItemType.READONLY_ACTION) {
                onClick.invoke(model)
            }
        }
    ) {
        Icon(
            modifier = Modifier.requiredSize(iconSize),
            painter = painterResource(id = model.drawableResId),
            contentDescription = remember(context, model.actionLabelResId, model.stateLabelResId) {
                model.getDescription(context)
            }
        )
    }

    // Detect long press
    // https://stackoverflow.com/a/76395585
    LaunchedEffect(model, interactionSource) {
        interactionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    delay(viewConfig.longPressTimeoutMillis)

                    if (isActive) {
                        Toast
                            .makeText(context, model.getDescription(context), Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionListButton(
    model: ActionButtonViewModel,
    isClickable: Boolean = true,
    onClick: (ActionButtonViewModel) -> Unit
) {
    val context = LocalContext.current

    Button(
        modifier = Modifier.fillMaxWidth(),
        enabled = model.buttonState != null,
        colors = model.buttonState?.let { state ->
            actionButtonColors(state)
        } ?: run {
            // Indeterminate state
            indeterminateActionButtonColors()
        },
        label = {
            Text(
                text = model.actionLabelResId.takeIf { it != 0 }?.let {
                    stringResource(id = it)
                } ?: ""
            )
        },
        secondaryLabel = model.stateLabelResId.takeIf { it != 0 }?.let {
            {
                Text(text = stringResource(id = it))
            }
        },
        icon = {
            Icon(
                modifier = Modifier.requiredSize(24.dp),
                painter = painterResource(id = model.drawableResId),
                contentDescription = remember(
                    context,
                    model.actionLabelResId,
                    model.stateLabelResId
                ) {
                    model.getDescription(context)
                }
            )
        },
        onClick = {
            if (isClickable && model.getItemViewType() != ActionItemType.READONLY_ACTION) {
                onClick.invoke(model)
            }
        }
    )
}

@Composable
private fun DashboardSettings(
    dashboardState: DashboardState,
    scrollState: ScrollState,
    onDashSettingsClick: () -> Unit = {},
    onDashTileSettingsClick: () -> Unit = {}
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        LayoutPreferenceButton(dashboardState.isGridLayout, scrollState)
        DashboardConfigButton(onDashSettingsClick)
        TileDashboardConfigButton(onDashTileSettingsClick)
        MediaControllerSwitch()
    }
}

@Composable
private fun LayoutPreferenceButton(
    isGridLayout: Boolean,
    scrollState: ScrollState
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(text = stringResource(id = R.string.pref_layout))
        },
        secondaryLabel = {
            Text(
                text = if (isGridLayout) {
                    stringResource(id = R.string.option_grid)
                } else {
                    stringResource(id = R.string.option_list)
                }
            )
        },
        icon = {
            Icon(
                painter = if (isGridLayout) {
                    painterResource(id = R.drawable.ic_apps_white_24dp)
                } else {
                    painterResource(id = R.drawable.ic_view_list_white_24dp)
                },
                contentDescription = if (isGridLayout) {
                    stringResource(id = R.string.option_grid)
                } else {
                    stringResource(id = R.string.option_list)
                }
            )
        },
        onClick = {
            AnalyticsLogger.logEvent("dash_layout_btn_clicked", Bundle().apply {
                putBoolean("isGridLayout", isGridLayout)
            })

            Settings.setGridLayout(!isGridLayout)

            lifecycleOwner.lifecycleScope.launch {
                scrollState.scrollTo(0)
            }
        }
    )
}

@Composable
private fun DashboardConfigButton(
    onClick: () -> Unit = {}
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(text = stringResource(id = R.string.pref_title_dasheditor))
        },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                contentDescription = stringResource(id = R.string.pref_title_dasheditor)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun TileDashboardConfigButton(
    onClick: () -> Unit = {}
) {
    FilledTonalButton(
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(text = stringResource(id = R.string.pref_title_tiledasheditor))
        },
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.ic_baseline_edit_24),
                contentDescription = stringResource(id = R.string.pref_title_tiledasheditor)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun MediaControllerSwitch() {
    val context = LocalContext.current.applicationContext
    val mediaCtrlrComponent = remember {
        ComponentName(context, "com.thewizrd.simplewear.MediaControllerActivity")
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    var isChecked by remember { mutableStateOf(false) }

    SwitchButton(
        modifier = Modifier.fillMaxWidth(),
        checked = isChecked,
        onCheckedChange = {
            isChecked = !isChecked

            context.packageManager.setComponentEnabledSetting(
                mediaCtrlrComponent,
                if (isChecked) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
        },
        label = {
            Text(
                text = stringResource(id = R.string.pref_title_mediacontroller_launcher),
                maxLines = 10
            )
        }
    )

    LaunchedEffect(lifecycleOwner) {
        isChecked =
            context.packageManager.getComponentEnabledSetting(mediaCtrlrComponent) <= PackageManager.COMPONENT_ENABLED_STATE_ENABLED
    }
}

@Composable
private fun transparentButtonColors(): ButtonColors = ButtonDefaults.outlinedButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurface,
    secondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    iconColor = MaterialTheme.colorScheme.onSurface,
    disabledContentColor = MaterialTheme.colorScheme.onSurface,
    disabledSecondaryContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledIconColor = MaterialTheme.colorScheme.onSurface,
)

@Composable
private fun actionButtonColors(state: Boolean): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = if (state) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        contentColor = if (state) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
        secondaryContentColor = if (state) {
            MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
        },
        iconColor = if (state) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    )
}

@Composable
private fun indeterminateActionButtonColors(): ButtonColors {
    val color = MaterialTheme.colorScheme.primaryContainer

    return ButtonDefaults.buttonColors(
        disabledContainerColor = color,
        disabledContentColor = contentColorFor(color),
        disabledSecondaryContentColor = contentColorFor(color).copy(alpha = 0.8f),
        disabledIconColor = contentColorFor(color)
    )
}

@WearPreviewDevices
@Composable
private fun PreviewDashboardScreen() {
    val dashboardState = remember {
        DashboardState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            isStatusLoading = true,
            batteryStatus = BatteryStatus(100, false),
            isGridLayout = true,
            showBatteryState = true,
            isActionsClickable = true,
            actions = Actions.entries.map {
                ActionButtonViewModel.getViewModelFromAction(it)
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        DashboardScreen(
            dashboardState = dashboardState
        )
    }
}

@WearPreviewDevices
@Composable
private fun PreviewDashboardScreen_List() {
    val dashboardState = remember {
        DashboardState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            isStatusLoading = true,
            batteryStatus = BatteryStatus(100, false),
            isGridLayout = false,
            showBatteryState = true,
            isActionsClickable = true,
            actions = Actions.entries.map {
                ActionButtonViewModel.getViewModelFromAction(it)
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        DashboardScreen(
            dashboardState = dashboardState
        )
    }
}

@WearPreviewDevices
@Composable
private fun PreviewDashboardScreen_Indeterminate() {
    val dashboardState = remember {
        DashboardState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            isStatusLoading = true,
            batteryStatus = BatteryStatus(100, false),
            isGridLayout = true,
            showBatteryState = true,
            isActionsClickable = true,
            actions = Actions.entries.map {
                ActionButtonViewModel.getViewModelFromAction(it).apply {
                    buttonState = null
                }
            }
        )
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        DashboardScreen(
            dashboardState = dashboardState
        )
    }
}

private fun ActionButtonViewModel.getItemViewType(): Int {
    return when (this.actionType) {
        Actions.WIFI, Actions.BLUETOOTH, Actions.MOBILEDATA, Actions.TORCH, Actions.HOTSPOT -> {
            ActionItemType.TOGGLE_ACTION
        }

        Actions.LOCATION -> ActionItemType.TOGGLE_ACTION
        Actions.LOCKSCREEN, Actions.MUSICPLAYBACK, Actions.SLEEPTIMER, Actions.APPS, Actions.PHONE,
        Actions.GESTURES, Actions.TIMEDACTION -> {
            ActionItemType.ACTION
        }

        Actions.VOLUME, Actions.BRIGHTNESS -> ActionItemType.VALUE_ACTION
        Actions.DONOTDISTURB -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                ActionItemType.MULTICHOICE_ACTION
            } else {
                ActionItemType.TOGGLE_ACTION
            }
        }

        Actions.RINGER -> ActionItemType.MULTICHOICE_ACTION
        else -> ActionItemType.TOGGLE_ACTION
    }
}

private object ActionItemType {
    const val ACTION = 0
    const val TOGGLE_ACTION = 1
    const val VALUE_ACTION = 2
    const val READONLY_ACTION = 3
    const val MULTICHOICE_ACTION = 4
}