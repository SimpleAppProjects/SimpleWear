@file:OptIn(
    ExperimentalHorologistApi::class,
    ExperimentalWearFoundationApi::class,
    ExperimentalWearMaterialApi::class
)

package com.thewizrd.simplewear.ui.simplewear

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rememberRevealState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.SwipeToRevealChip
import androidx.wear.compose.material.SwipeToRevealDefaults
import androidx.wear.compose.material.SwipeToRevealPrimaryAction
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.TimePicker
import com.google.android.horologist.composables.TimePickerWith12HourClock
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults.listTextPadding
import com.google.android.horologist.compose.layout.fillMaxRectangle
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.ResponsiveListHeader
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.viewmodels.TimedActionUiViewModel
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

    LaunchedEffect(Unit) {
        timedActionUiViewModel.refreshState()
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timedActionUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.TimedActionAddPath,
                    WearableHelper.TimedActionDeletePath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.SUCCESS_ANIMATION)
                                    .showOn(activity)
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            activity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(activity.getString(R.string.error_permissiondenied))
                                    .showOn(activity)

                                timedActionUiViewModel.openAppOnPhone(
                                    activity,
                                    showAnimation = false
                                )
                            }

                            else -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.FAILURE_ANIMATION)
                                    .setMessage(activity.getString(R.string.error_actionfailed))
                                    .showOn(activity)
                            }
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
    val scrollState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Text,
            last = ScalingLazyColumnDefaults.ItemType.SingleButton
        )
    )

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        timeText = { TimeText(modifier = Modifier.scrollAway { scrollState }) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState.state) }
    ) {
        ScalingLazyColumn(
            modifier = modifier
                .fillMaxSize()
                .rotaryScrollable(
                    focusRequester = rememberActiveFocusRequester(),
                    behavior = RotaryScrollableDefaults.behavior(scrollState)
                ),
            columnState = scrollState
        ) {
            item {
                Box(
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Text(
                        modifier = Modifier.listTextPadding(),
                        text = stringResource(id = R.string.title_actions),
                        style = MaterialTheme.typography.button
                    )
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
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
            item {
                Button(onClick = onAddAction) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add_white_24dp),
                        contentDescription = stringResource(id = R.string.label_add_action)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTimedActionUi(
    modifier: Modifier = Modifier,
    onAddAction: () -> Unit = {}
) {
    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) }
    ) {
        Column(
            modifier = Modifier.fillMaxRectangle()
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
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colors.onBackground
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
                    painter = painterResource(id = R.drawable.ic_add_white_24dp),
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
    val revealState = rememberRevealState()

    val chipColors = ChipDefaults.secondaryChipColors()

    SwipeToRevealChip(
        primaryAction = {
            SwipeToRevealPrimaryAction(
                revealState = revealState,
                onClick = {
                    onActionDelete.invoke(timedAction)
                },
                icon = {
                    Icon(
                        imageVector = SwipeToRevealDefaults.Delete,
                        contentDescription = stringResource(id = R.string.action_delete)
                    )
                },
                label = {
                    Text(text = stringResource(id = R.string.action_delete))
                }
            )
        },
        revealState = revealState,
        onFullSwipe = {
            onActionDelete.invoke(timedAction)
        }
    ) {
        Chip(
            modifier = Modifier.fillMaxWidth(),
            colors = chipColors,
            border = ChipDefaults.chipBorder(),
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
                        tint = chipColors.iconColor(enabled = true).value
                    )
                }
                Spacer(modifier = Modifier.size(6.dp))
                Column(
                    modifier = Modifier.weight(1f, fill = true)
                ) {
                    Row {
                        Text(
                            text = stringResource(id = model.actionLabelResId),
                            style = MaterialTheme.typography.button,
                            color = chipColors.contentColor(enabled = true).value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (model.stateLabelResId != 0) {
                        Row {
                            Text(
                                text = stringResource(id = model.stateLabelResId),
                                style = MaterialTheme.typography.caption1,
                                color = chipColors.secondaryContentColor(enabled = true).value,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row {
                        Text(
                            text = timeString,
                            style = MaterialTheme.typography.caption2,
                            color = chipColors.secondaryContentColor(enabled = true).value,
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

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timedActionUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.TimedActionUpdatePath,
                    WearableHelper.TimedActionDeletePath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.SUCCESS_ANIMATION)
                                    .showOn(activity)

                                navController.popBackStack()
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            activity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(activity.getString(R.string.error_permissiondenied))
                                    .showOn(activity)

                                timedActionUiViewModel.openAppOnPhone(
                                    activity,
                                    showAnimation = false
                                )
                            }

                            else -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.FAILURE_ANIMATION)
                                    .setMessage(activity.getString(R.string.error_actionfailed))
                                    .showOn(activity)
                            }
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
    val scrollState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Text,
            last = ScalingLazyColumnDefaults.ItemType.SingleButton,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically)
    )
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

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        timeText = { TimeText(modifier = Modifier.scrollAway { scrollState }) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = scrollState.state) }
    ) {
        ScalingLazyColumn(
            columnState = scrollState
        ) {
            item {
                ResponsiveListHeader {
                    Text(text = stringResource(id = R.string.title_edit_action))
                }
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false,
                    colors = ChipDefaults.secondaryChipColors(),
                    border = ChipDefaults.chipBorder(),
                    onClick = {}
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                modifier = Modifier.align(Alignment.Center),
                                painter = painterResource(id = model.drawableResId),
                                contentDescription = remember(
                                    context,
                                    model.actionLabelResId,
                                    model.stateLabelResId
                                ) {
                                    model.getDescription(context)
                                },
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Column(
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Row {
                                Text(
                                    text = stringResource(id = R.string.label_action),
                                    style = MaterialTheme.typography.button,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Row {
                                Text(
                                    text = stringResource(id = model.actionLabelResId),
                                    style = MaterialTheme.typography.caption2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                Chip(
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    border = ChipDefaults.chipBorder(),
                    onClick = {
                        showTimePicker = true
                    }
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Icon(
                                modifier = Modifier.align(Alignment.Center),
                                painter = painterResource(id = R.drawable.ic_alarm_white_24dp),
                                contentDescription = stringResource(id = R.string.label_time),
                                tint = MaterialTheme.colors.onSurface
                            )
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Column(
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Row {
                                Text(
                                    text = stringResource(id = R.string.label_time),
                                    style = MaterialTheme.typography.button,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            Row {
                                Text(
                                    text = timeString,
                                    style = MaterialTheme.typography.caption2,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }
            }

            item {
                ListHeader {
                    Text(
                        text = stringResource(id = R.string.label_state),
                        style = MaterialTheme.typography.caption1
                    )
                }
            }

            when (action.action) {
                is ToggleAction -> {
                    item {
                        val tA = remember(action.action, actionState) {
                            action.action as ToggleAction
                        }

                        ToggleChip(
                            checked = tA.isEnabled,
                            onCheckedChanged = {
                                tA.isEnabled = it
                                actionState = it
                            },
                            label = stringResource(id = model.stateLabelResId),
                            toggleControl = ToggleChipToggleControl.Switch
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

                        ToggleChip(
                            checked = mA.choice == choice,
                            onCheckedChanged = {
                                mA.choice = choice
                                actionState = choice
                            },
                            label = stringResource(id = multiActionModel.stateLabelResId),
                            toggleControl = ToggleChipToggleControl.Radio
                        )
                    }
                }

                else -> {
                    item {
                        com.google.android.horologist.compose.material.Chip(
                            label = stringResource(id = R.string.label_action_not_supported),
                            onClick = {},
                            enabled = false
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.size(16.dp))
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        12.dp,
                        Alignment.CenterHorizontally
                    )
                ) {
                    com.google.android.horologist.compose.material.Button(
                        id = R.drawable.ic_check_white_24dp,
                        contentDescription = stringResource(id = android.R.string.ok),
                        onClick = {
                            onActionUpdate.invoke(action)
                        },
                        colors = ButtonDefaults.primaryButtonColors()
                    )
                    com.google.android.horologist.compose.material.Button(
                        id = R.drawable.ic_delete_outline,
                        contentDescription = stringResource(id = R.string.action_delete),
                        colors = ButtonDefaults.primaryButtonColors(
                            backgroundColor = MaterialTheme.colors.error,
                            contentColor = MaterialTheme.colors.onError
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
        showDialog = showTimePicker,
        onDismissRequest = {
            showTimePicker = false
        }
    ) {
        val localTime = remember(action.timeInMillis) {
            Instant.ofEpochMilli(action.timeInMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalTime()
        }

        if (is24Hour) {
            TimePicker(
                time = localTime,
                onTimeConfirm = {
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
                showSeconds = false
            )
        } else {
            TimePickerWith12HourClock(
                time = localTime,
                onTimeConfirm = {
                    val today = LocalDate.now()
                    val now = LocalTime.now()

                    action.timeInMillis = if (it < now) {
                        today.plusDays(1).atTime(it).atZone(ZoneId.systemDefault()).toInstant()
                            .toEpochMilli()
                    } else {
                        today.atTime(it).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }

                    showTimePicker = false
                }
            )
        }
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

    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
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

    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
        TimedActionDetailUi(
            action = action
        )
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewEmptyTimedActionUi() {
    CompositionLocalProvider(
        LocalContentColor provides Color.White
    ) {
        EmptyTimedActionUi()
    }
}