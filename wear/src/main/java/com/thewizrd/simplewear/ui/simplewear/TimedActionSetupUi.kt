package com.thewizrd.simplewear.ui.simplewear

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.PagerState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonColors
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimePicker
import androidx.wear.compose.material3.TimePickerType
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.HorizontalPagerScreen
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
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

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

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
                        confirmationViewModel.showFailure()
                        navController.popBackStack()
                    }
                }
            }
        },
        onCancel = {
            navController.popBackStack()
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
                    WearableHelper.TimedActionAddPath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

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

                                timedActionUiViewModel.openAppOnPhone(
                                    activity,
                                    showAnimation = false
                                )
                            }

                            else -> {
                                confirmationViewModel.showFailure(
                                    message = context.getString(R.string.error_actionfailed)
                                )
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

    val focusRequester = rememberFocusRequester()

    HorizontalPagerScreen(
        modifier = modifier,
        pagerState = pagerState,
        userScrollEnabled = false,
        hidePagerIndicator = true
    ) { pageIdx ->
        AnimatedPage(pageIdx, pagerState) {
            when (pageIdx) {
                // Time
                0 -> {
                    TimePicker(
                        onTimePicked = {
                            scheduledTime = System.currentTimeMillis() +
                                    TimeUnit.HOURS.toMillis(it.hour.toLong()) +
                                    TimeUnit.MINUTES.toMillis(it.minute.toLong())
                            compositionScope.launch {
                                pagerState.animateScrollToPage(pageIdx + 1)
                            }
                        },
                        initialTime = LocalTime.of(0, 15),
                        timePickerType = TimePickerType.HoursMinutes24H
                    )
                }
                // Actions
                1 -> {
                    val columnState = rememberTransformingLazyColumnState()
                    val contentPadding = rememberResponsiveColumnPadding(
                        first = ColumnItemType.ListHeader,
                        last = ColumnItemType.Button,
                    )
                    val transformationSpec = rememberTransformationSpec()

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

                                FilledTonalButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
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
                                        selectedAction = Action.getDefaultAction(it)
                                        compositionScope.launch {
                                            pagerState.animateScrollToPage(pageIdx + 1)
                                        }
                                    }
                                )
                            }
                        }

                        LaunchedEffect(pagerState, pagerState.targetPage) {
                            if (pagerState.targetPage == pageIdx) {
                                focusRequester.requestFocus()
                            }
                        }
                    }
                }
                // State
                2 -> {
                    val columnState = rememberTransformingLazyColumnState()
                    val contentPadding = rememberResponsiveColumnPadding(
                        first = ColumnItemType.ListHeader,
                        last = ColumnItemType.Button,
                    )
                    val transformationSpec = rememberTransformationSpec()

                    var actionState by remember { mutableStateOf<Any?>(null) }
                    var initActionState by remember { mutableStateOf<Any?>(null) }

                    val model = remember(selectedAction, actionState) {
                        ActionButtonViewModel(selectedAction)
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
                                    Text(text = stringResource(id = R.string.title_confirm_action))
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
                                            painter = painterResource(id = model.drawableResId),
                                            contentDescription = stringResource(id = model.actionLabelResId)
                                        )
                                    },
                                    label = {
                                        Text(text = stringResource(id = R.string.label_action))
                                    },
                                    secondaryLabel = {
                                        Text(text = stringResource(id = model.actionLabelResId))
                                    },
                                    colors = disabledButtonColors(),
                                    onClick = {}
                                )
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

                                FilledTonalButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                    enabled = false,
                                    icon = {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_alarm_white_24dp),
                                            contentDescription = stringResource(id = R.string.label_time),
                                        )
                                    },
                                    label = {
                                        Text(text = stringResource(id = R.string.label_time))
                                    },
                                    secondaryLabel = {
                                        Text(text = timeString)
                                    },
                                    colors = disabledButtonColors(),
                                    onClick = {}
                                )
                            }

                            item {
                                SwitchButton(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                    checked = shouldSetInitialAction,
                                    onCheckedChange = {
                                        initialAction =
                                            Action.getDefaultAction(selectedAction.actionType)
                                        shouldSetInitialAction = it
                                    },
                                    label = {
                                        Text(text = stringResource(id = R.string.title_set_initial_state))
                                    }
                                )
                            }

                            if (shouldSetInitialAction) {
                                item {
                                    ListHeader(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .transformedHeight(this, transformationSpec),
                                        transformation = SurfaceTransformation(transformationSpec),
                                    ) {
                                        Text(
                                            text = stringResource(id = R.string.title_initial_state),
                                            style = MaterialTheme.typography.labelSmall
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

                                            SwitchButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .transformedHeight(this, transformationSpec),
                                                transformation = SurfaceTransformation(
                                                    transformationSpec
                                                ),
                                                checked = tA.isEnabled,
                                                onCheckedChange = {
                                                    tA.isEnabled = it
                                                    initActionState = it
                                                },
                                                label = {
                                                    Text(text = stringResource(id = actionModel.stateLabelResId))
                                                }
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

                                            RadioButton(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .transformedHeight(this, transformationSpec),
                                                transformation = SurfaceTransformation(
                                                    transformationSpec
                                                ),
                                                selected = mA.choice == choice,
                                                onSelect = {
                                                    mA.choice = choice
                                                    initActionState = true
                                                },
                                                label = {
                                                    Text(text = stringResource(id = multiActionModel.stateLabelResId))
                                                }
                                            )
                                        }
                                    }

                                    else -> {}
                                }
                            }

                            item {
                                ListHeader(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .transformedHeight(this, transformationSpec),
                                    transformation = SurfaceTransformation(transformationSpec),
                                ) {
                                    Text(
                                        text = stringResource(id = R.string.title_scheduled_state),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }

                            when (selectedAction) {
                                is ToggleAction -> {
                                    item {
                                        val tA = remember(selectedAction, actionState) {
                                            selectedAction as ToggleAction
                                        }

                                        SwitchButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .transformedHeight(this, transformationSpec),
                                            transformation = SurfaceTransformation(
                                                transformationSpec
                                            ),
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

                                        RadioButton(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .transformedHeight(this, transformationSpec),
                                            transformation = SurfaceTransformation(
                                                transformationSpec
                                            ),
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
                                            transformation = SurfaceTransformation(
                                                transformationSpec
                                            ),
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
                                Spacer(modifier = Modifier.size(16.dp))
                            }

                            if (selectedAction is ToggleAction || selectedAction is MultiChoiceAction) {
                                item {
                                    FilledIconButton(
                                        content = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_check_white_24dp),
                                                contentDescription = stringResource(id = android.R.string.ok),
                                            )
                                        },
                                        onClick = {
                                            onAddAction.invoke(
                                                initialAction,
                                                TimedAction(scheduledTime, selectedAction)
                                            )
                                        }
                                    )
                                }
                            } else {
                                item {
                                    FilledTonalIconButton(
                                        content = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_close_white_24dp),
                                                contentDescription = stringResource(id = android.R.string.cancel),
                                            )
                                        },
                                        onClick = onCancel
                                    )
                                }
                            }
                        }

                        LaunchedEffect(pagerState, pagerState.targetPage) {
                            if (pagerState.targetPage == pageIdx) {
                                focusRequester.requestFocus()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun disabledButtonColors(): ButtonColors {
    return ButtonDefaults.filledTonalButtonColors(
        disabledContentColor = MaterialTheme.colorScheme.onSurface,
        disabledSecondaryContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        disabledIconColor = MaterialTheme.colorScheme.onSurface,
    )
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