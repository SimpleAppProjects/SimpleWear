package com.thewizrd.simplewear.ui.simplewear

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.fillMaxRectangle
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.getSerializableCompat
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.compose.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.TimedActionUiViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun TimedActionUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val timedActionUiViewModel = activityViewModel<TimedActionUiViewModel>()
    val uiState by timedActionUiViewModel.uiState.collectAsState()
    val actions by timedActionUiViewModel.actions.collectAsState(initial = emptyList())

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    LoadingContent(
        empty = actions.isEmpty(),
        emptyContent = {
            EmptyTimedActionUi(
                modifier = modifier,
                onAddAction = {
                    navController.navigate(Screen.TimedActionSetup.route)
                }
            )
        },
        loading = uiState.isLoading
    ) {
        TimedActionUi(
            modifier = modifier,
            actions = actions,
            onActionClicked = {
                navController.navigate(
                    route = Screen.TimedActionDetail.getRoute(it)
                )
            },
            onActionDelete = {
                timedActionUiViewModel.requestDeleteAction(it)
            },
            onAddAction = {
                navController.navigate(Screen.TimedActionSetup.route)
            }
        )
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(Unit) {
        timedActionUiViewModel.refreshState()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timedActionUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.TimedActionAddPath,
                    WearableHelper.TimedActionDeletePath -> {
                        val status =
                            event.data.getSerializableCompat(EXTRA_STATUS, ActionStatus::class.java)

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                confirmationViewModel.showSuccess()
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                confirmationViewModel.showOpenOnPhoneForFailure(
                                    message = context.getString(
                                        R.string.error_permissiondenied
                                    )
                                )

                                timedActionUiViewModel.openAppOnPhone(showAnimation = false)
                            }

                            else -> {
                                confirmationViewModel.showFailure(
                                    message = context.getString(R.string.error_actionfailed)
                                )
                            }
                        }
                    }

                    WearableListenerViewModel.ACTION_SHOWCONFIRMATION -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)

                        JSONParser.deserializer(jsonData, ConfirmationData::class.java)?.let {
                            confirmationViewModel.showConfirmation(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimedActionUi(
    modifier: Modifier = Modifier,
    actions: List<TimedAction>,
    onActionClicked: (TimedAction) -> Unit = {},
    onActionDelete: (TimedAction) -> Unit = {},
    onAddAction: () -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        modifier = modifier,
        scrollState = columnState,
        contentPadding = contentPadding,
        edgeButton = {
            EdgeButton(onClick = onAddAction) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(id = R.string.label_add_action)
                )
            }
        }
    ) {
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = stringResource(id = R.string.title_actions))
                }
            }
            items(
                items = actions,
                key = { item -> item.action.actionType }
            ) {
                TimedActionChip(
                    timedAction = it,
                    onActionClicked = onActionClicked,
                    onActionDelete = onActionDelete
                )
            }
        }
    }
}

@Composable
private fun EmptyTimedActionUi(
    modifier: Modifier = Modifier,
    onAddAction: () -> Unit = {}
) {
    ScreenScaffold(
        modifier = modifier
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxRectangle()
        ) {
            Box(
                modifier = Modifier
                    .height(48.dp)
                    .wrapContentSize()
                    .align(Alignment.CenterHorizontally)
                    .weight(1f, fill = true)
                    .padding(horizontal = 14.dp)
                    .semantics(mergeDescendants = true) { heading() }
            ) {
                Text(
                    text = stringResource(id = R.string.title_schedule_action),
                    textAlign = TextAlign.Center
                )
            }

            Button(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .weight(1f, fill = false)
                    .padding(top = 8.dp),
                onClick = onAddAction
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = stringResource(R.string.label_add_action)
                )
            }
        }
    }
}

@Composable
private fun TimedActionChip(
    timedAction: TimedAction,
    onActionClicked: (TimedAction) -> Unit = {},
    onActionDelete: (TimedAction) -> Unit,
) {
    val context = LocalContext.current

    val model = remember(timedAction) {
        ActionButtonViewModel(timedAction.action)
    }
    val timeString = remember(timedAction) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(
            LocalTime.ofInstant(
                Instant.ofEpochMilli(timedAction.timeInMillis),
                ZoneId.systemDefault()
            )
        )
    }

    val buttonColors = ButtonDefaults.filledTonalButtonColors()

    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = {
                    onActionDelete.invoke(timedAction)
                },
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(id = R.string.action_delete)
                    )
                },
                text = {
                    Text(text = stringResource(id = R.string.action_delete))
                }
            )
        },
        onSwipePrimaryAction = {
            onActionDelete.invoke(timedAction)
        }
    ) {
        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                onActionClicked.invoke(timedAction)
            }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxHeight()
            ) {
                Box(
                    modifier = Modifier.wrapContentSize(align = Alignment.Center)
                ) {
                    Icon(
                        modifier = Modifier.size(24.dp),
                        painter = painterResource(id = model.drawableResId),
                        contentDescription = remember(
                            context,
                            model.actionLabelResId,
                            model.stateLabelResId
                        ) {
                            model.getDescription(context)
                        },
                        tint = buttonColors.iconColor
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    Row {
                        Text(
                            text = stringResource(id = model.actionLabelResId),
                            style = MaterialTheme.typography.labelMedium,
                            color = buttonColors.contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (model.stateLabelResId != 0) {
                        Row {
                            Text(
                                text = stringResource(id = model.stateLabelResId),
                                style = MaterialTheme.typography.labelSmall,
                                color = buttonColors.secondaryContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.bodySmall,
                            color = buttonColors.secondaryContentColor,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimedActionDetailUi(
    modifier: Modifier = Modifier,
    action: TimedAction,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val timedActionUiViewModel = activityViewModel<TimedActionUiViewModel>()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    TimedActionDetailUi(
        modifier = modifier,
        action = action,
        onActionUpdate = {
            timedActionUiViewModel.requestUpdateAction(action)
        },
        onActionDelete = {
            timedActionUiViewModel.requestDeleteAction(action)
        }
    )

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timedActionUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.TimedActionUpdatePath,
                    WearableHelper.TimedActionDeletePath -> {
                        val status =
                            event.data.getSerializableCompat(EXTRA_STATUS, ActionStatus::class.java)

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                confirmationViewModel.showSuccess()
                                navController.popBackStack()
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                confirmationViewModel.showOpenOnPhoneForFailure(
                                    message = context.getString(
                                        R.string.error_permissiondenied
                                    )
                                )

                                timedActionUiViewModel.openAppOnPhone(showAnimation = false)
                            }

                            else -> {
                                confirmationViewModel.showFailure(
                                    message = context.getString(R.string.error_actionfailed)
                                )
                            }
                        }
                    }

                    WearableListenerViewModel.ACTION_SHOWCONFIRMATION -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)

                        JSONParser.deserializer(jsonData, ConfirmationData::class.java)?.let {
                            confirmationViewModel.showConfirmation(it)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimedActionDetailUi(
    modifier: Modifier = Modifier,
    action: TimedAction,
    onActionUpdate: (TimedAction) -> Unit = {},
    onActionDelete: (TimedAction) -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    val context = LocalContext.current
    val is24Hour = remember(context) {
        DateFormat.is24HourFormat(context)
    }

    var actionState by remember { mutableStateOf<Any?>(null) }
    var showTimePicker by remember { mutableStateOf(false) }

    val model = remember(action, actionState) {
        ActionButtonViewModel(action.action)
    }

    val timeString = remember(action.timeInMillis) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(
            LocalTime.ofInstant(
                Instant.ofEpochMilli(action.timeInMillis),
                ZoneId.systemDefault()
            )
        )
    }

    ScreenScaffold(
        scrollState = columnState,
        contentPadding = contentPadding
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = stringResource(id = R.string.title_edit_action))
                }
            }

            item {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    enabled = false,
                    icon = {
                        Icon(
                            modifier = Modifier.align(Alignment.Center),
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
                    label = {
                        Text(text = stringResource(id = R.string.label_action))
                    },
                    secondaryLabel = {
                        Text(text = stringResource(id = model.actionLabelResId))
                    },
                    onClick = {}
                )
            }

            item {
                FilledTonalButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                    onClick = {
                        showTimePicker = true
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_alarm_white_24dp),
                            contentDescription = stringResource(id = R.string.label_time)
                        )
                    },
                    label = {
                        Text(text = stringResource(id = R.string.label_time))
                    },
                    secondaryLabel = {
                        Text(text = timeString)
                    }
                )
            }

            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = stringResource(id = R.string.label_state))
                }
            }

            when (action.action) {
                is ToggleAction -> {
                    item {
                        val tA = remember(action.action, actionState) {
                            action.action as ToggleAction
                        }

                        SwitchButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            checked = tA.isEnabled,
                            onCheckedChange = {
                                tA.isEnabled = it
                                actionState = it
                            },
                            label = {
                                Text(text = stringResource(id = model.stateLabelResId))
                            }
                        )
                    }
                }

                is MultiChoiceAction -> {
                    items((action.action as MultiChoiceAction).numberOfStates) { choice ->
                        val mA = remember(action.action, actionState) {
                            action.action as MultiChoiceAction
                        }
                        val multiActionModel = remember(mA, choice) {
                            ActionButtonViewModel(MultiChoiceAction(mA.actionType, choice))
                        }

                        RadioButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            selected = mA.choice == choice,
                            onSelect = {
                                mA.choice = choice
                                actionState = choice
                            },
                            label = {
                                Text(text = stringResource(id = multiActionModel.stateLabelResId))
                            }
                        )
                    }
                }

                else -> {
                    item {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            label = {
                                Text(text = stringResource(id = R.string.label_action_not_supported))
                            },
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }

            item {
                Spacer(
                    modifier = Modifier.size(16.dp)
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    FilledIconButton(
                        content = {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_white_24dp),
                                contentDescription = stringResource(id = android.R.string.ok),
                            )
                        },
                        onClick = {
                            onActionUpdate.invoke(action)
                        }
                    )
                    FilledIconButton(
                        content = {
                            Icon(
                                painter = painterResource(R.drawable.ic_delete_outline),
                                contentDescription = stringResource(id = R.string.action_delete),
                            )
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        onClick = {
                            onActionDelete.invoke(action)
                        }
                    )
                }
            }
        }
    }

    Dialog(
        visible = showTimePicker,
        onDismissRequest = {
            showTimePicker = false
        }
    ) {
        val localTime = remember(action.timeInMillis) {
            Instant.ofEpochMilli(action.timeInMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }

        TimePicker(
            initialTime = localTime,
            onTimePicked = {
                val today = LocalDate.now()
                val now = LocalTime.now()

                action.timeInMillis = if (it < now) {
                    today.plusDays(1).atTime(it).atZone(ZoneId.systemDefault()).toInstant()
                        .toEpochMilli()
                } else {
                    today.atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }

                showTimePicker = false
            },
            timePickerType = if (is24Hour) {
                TimePickerType.HoursMinutes24H
            } else {
                TimePickerType.HoursMinutesAmPm12H
            }
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimedActionUi() {
    val currentTime = remember {
        System.currentTimeMillis()
    }

    val actions = remember {
        listOf(
            TimedAction(currentTime + (60000 * 1), ToggleAction(Actions.WIFI, true)),
            TimedAction(currentTime + (60000 * 5), ToggleAction(Actions.BLUETOOTH, false)),
            TimedAction(
                currentTime + (60000 * 10),
                MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value)
            ),
            TimedAction(
                currentTime + (60000 * 15),
                MultiChoiceAction(Actions.DONOTDISTURB, DNDChoice.ALARMS.value)
            ),
        )
    }

    WearAppTheme {
        TimedActionUi(
            actions = actions
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimedActionDetailUi() {
    val currentTime = remember {
        System.currentTimeMillis()
    }

    val action = remember {
        TimedAction(
            currentTime + (60000 * 15),
            MultiChoiceAction(Actions.DONOTDISTURB, DNDChoice.ALARMS.value)
        )
    }

    WearAppTheme {
        TimedActionDetailUi(
            action = action
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewEmptyTimedActionUi() {
    WearAppTheme {
        EmptyTimedActionUi()
    }
}