@file:OptIn(
    ExperimentalFoundationApi::class, ExperimentalHorologistApi::class,
    ExperimentalWearFoundationApi::class
)

package com.thewizrd.simplewear.ui.simplewear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.composables.TimePicker
import com.google.android.horologist.compose.layout.PagerScaffold
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.Button
import com.google.android.horologist.compose.material.Chip
import com.google.android.horologist.compose.material.ResponsiveListHeader
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.ui.components.ScalingLazyColumn
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.viewmodels.TimedActionUiViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.TimeUnit

@Composable
fun TimedActionSetupUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val compositionScope = rememberCoroutineScope()
    val timedActionUiViewModel = activityViewModel<TimedActionUiViewModel>()

    TimedActionSetupUi(
        modifier = modifier,
        onAddAction = { initialAction, timedAction ->
            if (initialAction != null) {
                timedActionUiViewModel.requestInitialAction(initialAction)
            }

            timedActionUiViewModel.requestAddAction(timedAction)
            compositionScope.launch {
                delay(15000)
                if (isActive) {
                    lifecycleOwner.lifecycleScope.launch {
                        activity.showConfirmationOverlay(false)
                        navController.popBackStack()
                    }
                }
            }
        },
        onCancel = {
            navController.popBackStack()
        }
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            timedActionUiViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableHelper.TimedActionAddPath -> {
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
private fun TimedActionSetupUi(
    modifier: Modifier = Modifier,
    pagerState: PagerState = rememberPagerState { 3 },
    supportedActions: List<Actions> = TimedAction.getSupportedActions(),
    onAddAction: (Action?, TimedAction) -> Unit = { _, _ -> },
    onCancel: () -> Unit = {}
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val compositionScope = rememberCoroutineScope()

    var scheduledTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedAction: Action by remember {
        mutableStateOf(Action.getDefaultAction(Actions.RINGER))
    }
    var shouldSetInitialAction by remember { mutableStateOf(false) }
    var initialAction: Action? by remember { mutableStateOf(null) }

    val focusRequester = remember { FocusRequester() }

    PagerScaffold(modifier = modifier) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false
        ) { index ->
            when (index) {
                // Time
                0 -> {
                    TimePicker(
                        onTimeConfirm = {
                            scheduledTime = System.currentTimeMillis() +
                                    TimeUnit.HOURS.toMillis(it.hour.toLong()) +
                                    TimeUnit.MINUTES.toMillis(it.minute.toLong())
                            compositionScope.launch {
                                pagerState.animateScrollToPage(index + 1)
                            }
                        },
                        time = LocalTime.of(0, 15),
                        showSeconds = false
                    )
                }
                // Actions
                1 -> {
                    val scrollState = rememberResponsiveColumnState(
                        contentPadding = ScalingLazyColumnDefaults.padding(
                            first = ScalingLazyColumnDefaults.ItemType.Text,
                            last = ScalingLazyColumnDefaults.ItemType.Chip
                        )
                    )

                    Box {
                        TimeText(modifier = Modifier.scrollAway { scrollState })

                        ScalingLazyColumn(
                            scrollState = scrollState,
                            focusRequester = focusRequester
                        ) {
                            item {
                                ResponsiveListHeader {
                                    Text(text = stringResource(id = R.string.title_actions))
                                }
                            }
                            items(
                                supportedActions,
                                key = { it }
                            ) {
                                val model = remember(it) {
                                    ActionButtonViewModel.getViewModelFromAction(it)
                                }

                                Chip(
                                    label = stringResource(id = model.actionLabelResId),
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = model.drawableResId),
                                            contentDescription = null
                                        )
                                    },
                                    colors = ChipDefaults.secondaryChipColors(),
                                    onClick = {
                                        selectedAction = Action.getDefaultAction(it)
                                        compositionScope.launch {
                                            pagerState.animateScrollToPage(index + 1)
                                        }
                                    }
                                )
                            }
                        }

                        LaunchedEffect(pagerState, pagerState.targetPage) {
                            if (pagerState.targetPage == index) {
                                focusRequester.requestFocus()
                            }
                        }
                    }
                }
                // State
                2 -> {
                    val scrollState = rememberResponsiveColumnState(
                        contentPadding = ScalingLazyColumnDefaults.padding(
                            first = ScalingLazyColumnDefaults.ItemType.Text,
                            last = ScalingLazyColumnDefaults.ItemType.Chip
                        )
                    )

                    var actionState by remember { mutableStateOf<Any?>(null) }
                    var initActionState by remember { mutableStateOf<Any?>(null) }

                    val model = remember(selectedAction, actionState) {
                        ActionButtonViewModel(selectedAction)
                    }

                    Box {
                        TimeText(modifier = Modifier.scrollAway { scrollState })

                        ScalingLazyColumn(
                            scrollState = scrollState,
                            focusRequester = focusRequester
                        ) {
                            item {
                                ResponsiveListHeader {
                                    Text(text = stringResource(id = R.string.title_confirm_action))
                                }
                            }

                            item {
                                androidx.wear.compose.material.Chip(
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
                                                contentDescription = null,
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
                                                    color = MaterialTheme.colors.onSurface.copy(
                                                        alpha = 0.75f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                val timeString = remember(scheduledTime) {
                                    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(
                                        LocalTime.ofInstant(
                                            Instant.ofEpochMilli(scheduledTime),
                                            ZoneId.systemDefault()
                                        )
                                    )
                                }

                                androidx.wear.compose.material.Chip(
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
                                                painter = painterResource(id = R.drawable.ic_alarm_white_24dp),
                                                contentDescription = null,
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
                                                    color = MaterialTheme.colors.onSurface.copy(
                                                        alpha = 0.75f
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                ToggleChip(
                                    checked = shouldSetInitialAction,
                                    onCheckedChanged = {
                                        initialAction =
                                            Action.getDefaultAction(selectedAction.actionType)
                                        shouldSetInitialAction = it
                                    },
                                    label = stringResource(id = R.string.title_set_initial_state),
                                    toggleControl = ToggleChipToggleControl.Switch
                                )
                            }

                            if (shouldSetInitialAction) {
                                item {
                                    ListHeader {
                                        Text(
                                            text = stringResource(id = R.string.title_initial_state),
                                            style = MaterialTheme.typography.caption1
                                        )
                                    }
                                }

                                when (initialAction) {
                                    is ToggleAction -> {
                                        item {
                                            val tA = remember(initialAction, initActionState) {
                                                initialAction as ToggleAction
                                            }
                                            val actionModel = remember(tA, tA.isEnabled) {
                                                ActionButtonViewModel(tA)
                                            }

                                            ToggleChip(
                                                checked = tA.isEnabled,
                                                onCheckedChanged = {
                                                    tA.isEnabled = it
                                                    initActionState = it
                                                },
                                                label = stringResource(id = actionModel.stateLabelResId),
                                                toggleControl = ToggleChipToggleControl.Switch
                                            )
                                        }
                                    }

                                    is MultiChoiceAction -> {
                                        items((initialAction as MultiChoiceAction).numberOfStates) { choice ->
                                            val mA = remember(initialAction, initActionState) {
                                                initialAction as MultiChoiceAction
                                            }
                                            val multiActionModel = remember(mA, choice) {
                                                ActionButtonViewModel(
                                                    MultiChoiceAction(
                                                        mA.actionType,
                                                        choice
                                                    )
                                                )
                                            }

                                            ToggleChip(
                                                checked = mA.choice == choice,
                                                onCheckedChanged = {
                                                    mA.choice = choice
                                                    initActionState = it
                                                },
                                                label = stringResource(id = multiActionModel.stateLabelResId),
                                                toggleControl = ToggleChipToggleControl.Radio
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }

                            item {
                                ListHeader {
                                    Text(
                                        text = stringResource(id = R.string.title_scheduled_state),
                                        style = MaterialTheme.typography.caption1
                                    )
                                }
                            }

                            when (selectedAction) {
                                is ToggleAction -> {
                                    item {
                                        val tA = remember(selectedAction, actionState) {
                                            selectedAction as ToggleAction
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
                                    items((selectedAction as MultiChoiceAction).numberOfStates) { choice ->
                                        val mA = remember(selectedAction, actionState) {
                                            selectedAction as MultiChoiceAction
                                        }
                                        val multiActionModel = remember(mA, choice) {
                                            ActionButtonViewModel(
                                                MultiChoiceAction(
                                                    mA.actionType,
                                                    choice
                                                )
                                            )
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
                                        Chip(
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

                            if (selectedAction is ToggleAction || selectedAction is MultiChoiceAction) {
                                item {
                                    Button(
                                        id = R.drawable.ic_check_white_24dp,
                                        contentDescription = stringResource(id = android.R.string.ok),
                                        onClick = {
                                            onAddAction.invoke(
                                                initialAction,
                                                TimedAction(scheduledTime, selectedAction)
                                            )
                                        },
                                        colors = ButtonDefaults.primaryButtonColors()
                                    )
                                }
                            } else {
                                item {
                                    Button(
                                        id = R.drawable.ic_close_white_24dp,
                                        contentDescription = stringResource(id = android.R.string.cancel),
                                        onClick = onCancel,
                                        colors = ButtonDefaults.secondaryButtonColors()
                                    )
                                }
                            }
                        }

                        LaunchedEffect(pagerState, pagerState.targetPage) {
                            if (pagerState.targetPage == index) {
                                focusRequester.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimedActionSetupTimePickerUi() {
    TimedActionSetupUi(
        pagerState = rememberPagerState(initialPage = 0) { 3 }
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimedActionSetupActionListUi() {
    TimedActionSetupUi(
        pagerState = rememberPagerState(initialPage = 1) { 3 }
    )
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewTimedActionConfirmationUi() {
    TimedActionSetupUi(
        pagerState = rememberPagerState(initialPage = 2) { 3 }
    )
}