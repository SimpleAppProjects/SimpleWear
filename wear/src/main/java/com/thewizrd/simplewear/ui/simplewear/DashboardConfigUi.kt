package com.thewizrd.simplewear.ui.simplewear

import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.motionEventSpy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.compose.LazyGridScrollIndicator
import com.thewizrd.simplewear.ui.compose.LazyGridScrollInfoProvider
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.ReorderHapticFeedbackType
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.ui.utils.rememberReorderHapticFeedback
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.util.Collections

private val MAX_BUTTONS = Actions.entries.size
private val DEFAULT_TILES = Actions.entries

@Composable
fun DashboardConfigUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    var tileConfig by remember {
        mutableStateOf(Settings.getDashboardConfig() ?: DEFAULT_TILES)
    }

    DashboardConfigUi(
        modifier = modifier,
        tileConfig = tileConfig,
        onSaveItems = { items, isBatteryVisible ->
            if (items.isEmpty()) {
                Settings.setDashboardConfig(null)
            } else {
                Settings.setDashboardConfig(items)
            }

            Settings.setShowBatStatus(isBatteryVisible)

            navController.popBackStack()
        }
    )

    LaunchedEffect(lifecycleOwner) {
        Settings.getDashboardConfigFlow().collect {
            tileConfig = it ?: DEFAULT_TILES
        }
    }
}

@Composable
private fun DashboardConfigUi(
    modifier: Modifier = Modifier,
    lazyGridState: LazyGridState = rememberLazyGridState(),
    focusRequester: FocusRequester = rememberFocusRequester(),
    tileConfig: List<Actions> = DEFAULT_TILES,
    initialBatteryStatusShown: Boolean = Settings.isShowBatStatus(),
    onSaveItems: (actions: List<Actions>, showBatteryStatus: Boolean) -> Unit = { _, _ -> }
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = rememberReorderHapticFeedback()

    var showConfirmation by remember { mutableStateOf(false) }
    var showAddTileDialog by remember { mutableStateOf(false) }

    val userTileConfigList: MutableList<Any> =
        remember(tileConfig) { tileConfig.toMutableStateList() }
    val selectionList =
        remember { MutableList(MAX_BUTTONS) { false }.toMutableStateList() }

    var isBatteryVisible by remember { mutableStateOf(initialBatteryStatusShown) }
    var batterySelected by remember { mutableStateOf(false) }

    val reorderableGridState = rememberReorderableLazyGridState(
        lazyGridState = lazyGridState
    ) { from, to ->
        // Offset index by 2 to account for header & battery state
        Collections.swap(userTileConfigList, from.index - 2, to.index - 2)
        haptic.performHapticFeedback(ReorderHapticFeedbackType.MOVE)
    }

    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button
    )

    ScreenScaffold(
        scrollInfoProvider = LazyGridScrollInfoProvider(lazyGridState),
        scrollIndicator = {
            LazyGridScrollIndicator(lazyGridState = lazyGridState)
        },
        contentPadding = contentPadding
    ) { contentPadding ->
        LazyVerticalGrid(
            modifier = modifier
                .fillMaxSize()
                .rotaryScrollable(RotaryScrollableDefaults.behavior(lazyGridState), focusRequester)
                .motionEventSpy { event ->
                    if (event.action == MotionEvent.ACTION_DOWN) {
                        selectionList.replaceAll { false }
                        batterySelected = false
                    }
                },
            columns = GridCells.Fixed(3),
            state = lazyGridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            userScrollEnabled = true
        ) {
            item(span = { GridItemSpan(3) }) {
                ListHeader(modifier = Modifier.fillMaxWidth()) {
                    Text(text = stringResource(id = R.string.title_dash_config))
                }
            }

            // Battery State
            item(span = { GridItemSpan(3) }) {
                AnimatedVisibility(
                    visible = batterySelected,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(id = R.string.action_remove_batt_state))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close_white_24dp),
                                contentDescription = stringResource(id = R.string.action_remove_batt_state)
                            )
                        },
                        onClick = {
                            isBatteryVisible = false
                            batterySelected = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primaryContainer,
                            iconColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                }

                AnimatedVisibility(
                    visible = !batterySelected && isBatteryVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    FilledTonalButton(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(id = R.string.title_batt_state))
                        },
                        icon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_battery_std_white_24dp),
                                contentDescription = stringResource(id = R.string.title_batt_state)
                            )
                        },
                        onClick = {
                            batterySelected = true
                        }
                    )
                }

                AnimatedVisibility(
                    visible = !batterySelected && !isBatteryVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = stringResource(id = R.string.action_add_batt_state))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(id = R.string.action_add_batt_state)
                            )
                        },
                        onClick = {
                            isBatteryVisible = true
                            batterySelected = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White,
                            contentColor = MaterialTheme.colorScheme.primaryContainer,
                            iconColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    )
                }
            }

            itemsIndexed(
                items = userTileConfigList,
                key = { index, item -> (item as? Actions) ?: index },
                span = { index, _ ->
                    GridItemSpan(1)
                }
            ) { index, item ->
                if (item is Actions) {
                    val model = remember(item) {
                        ActionButtonViewModel.getViewModelFromAction(item)
                    }

                    ReorderableItem(
                        modifier = Modifier.wrapContentSize(),
                        state = reorderableGridState,
                        key = item
                    ) { _ ->
                        Box(
                            modifier = Modifier
                                .padding(4.dp)
                                .draggableHandle(
                                    onDragStarted = {
                                        haptic.performHapticFeedback(ReorderHapticFeedbackType.START)
                                    },
                                    onDragStopped = {
                                        haptic.performHapticFeedback(ReorderHapticFeedbackType.END)
                                    },
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            AnimatedVisibility(
                                visible = selectionList[index],
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                FilledIconButton(
                                    onClick = {
                                        userTileConfigList.removeAt(index)
                                        if (!userTileConfigList.contains("")) {
                                            userTileConfigList.add("")
                                        }
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = Color.White,
                                        contentColor = MaterialTheme.colorScheme.primaryContainer
                                    )
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close_white_24dp),
                                        contentDescription = stringResource(id = android.R.string.cancel),
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = !selectionList[index],
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                FilledTonalButton(
                                    onClick = { selectionList[index] = true }
                                ) {
                                    Icon(
                                        painter = painterResource(id = model.drawableResId),
                                        contentDescription = stringResource(id = model.actionLabelResId)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .wrapContentSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        FilledIconButton(
                            onClick = {
                                showAddTileDialog = true
                            },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(id = R.string.action_add_batt_state)
                            )
                        }
                    }
                }
            }

            item(span = { GridItemSpan(3) }) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    AlertDialogDefaults.DismissButton(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onClick = {
                            showConfirmation = true
                        },
                        content = {
                            Icon(
                                modifier = Modifier.size(28.dp),
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = stringResource(R.string.message_reset_to_default),
                            )
                        }
                    )
                    AlertDialogDefaults.ConfirmButton(
                        modifier = Modifier.align(Alignment.CenterVertically),
                        onClick = {
                            onSaveItems(
                                userTileConfigList.filterIsInstance<Actions>(),
                                isBatteryVisible
                            )
                        }
                    )
                }
            }
        }

        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.repeatOnLifecycle(state = Lifecycle.State.RESUMED) {
                focusRequester.requestFocus()
            }
        }
    }

    AlertDialog(
        visible = showConfirmation,
        onDismissRequest = {
            showConfirmation = false
        },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(
                onClick = {
                    // Reset state
                    userTileConfigList.clear()
                    userTileConfigList.addAll(DEFAULT_TILES)
                    batterySelected = false
                    isBatteryVisible = true

                    // Reset settings
                    Settings.setDashboardConfig(null)
                    Settings.setShowBatStatus(true)

                    showConfirmation = false
                }
            )
        },
        dismissButton = {
            AlertDialogDefaults.DismissButton(
                onClick = {
                    showConfirmation = false
                }
            )
        },
        title = {
            Text(text = stringResource(id = R.string.message_reset_to_default))
        }
    )

    if (showAddTileDialog) {
        val allowedActions = Actions.entries.toMutableList()
        // Remove current actions
        allowedActions.removeAll(userTileConfigList.filterIsInstance<Actions>())

        AlertDialog(
            modifier = Modifier.fillMaxSize(),
            visible = showAddTileDialog,
            onDismissRequest = { showAddTileDialog = false },
            title = { Text(text = stringResource(id = R.string.title_actions)) },
            edgeButton = {
                EdgeButton(
                    onClick = { showAddTileDialog = false }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close_white_24dp),
                        contentDescription = stringResource(android.R.string.cancel)
                    )
                }
            }
        ) {
            items(allowedActions) { action ->
                val model = remember(action) {
                    ActionButtonViewModel.getViewModelFromAction(action)
                }

                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(id = model.actionLabelResId))
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = model.drawableResId),
                            contentDescription = stringResource(id = model.actionLabelResId)
                        )
                    },
                    onClick = {
                        val index = userTileConfigList.indexOfFirst { it !is Actions }
                        if (index >= 0) {
                            userTileConfigList[index] = action
                        }
                        addAddButtonIfNeeded(userTileConfigList)
                        showAddTileDialog = false
                    }
                )
            }
        }
    }

    LaunchedEffect(tileConfig) {
        addAddButtonIfNeeded(userTileConfigList)
    }
}

private fun addAddButtonIfNeeded(userTileConfigList: MutableList<Any>) {
    if (userTileConfigList.size < MAX_BUTTONS && !userTileConfigList.contains("")) {
        userTileConfigList.add("")
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewDashboardConfigUi() {
    DashboardConfigUi(
        initialBatteryStatusShown = true
    )
}