package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Stepper
import androidx.wear.compose.material3.Text
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.ValueActionUiState
import com.thewizrd.simplewear.viewmodels.ValueActionViewModel
import com.thewizrd.simplewear.viewmodels.ValueActionVolumeViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ValueActionScreen(
    modifier: Modifier = Modifier,
    actionType: Actions,
    audioStreamType: AudioStreamType? = null
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val valueActionViewModel = viewModel<ValueActionViewModel>()
    val volumeViewModel = remember(context, valueActionViewModel) {
        ValueActionVolumeViewModel(context, valueActionViewModel)
    }

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    ScreenScaffold { contentPadding ->
        ValueActionScreen(
            modifier = modifier.padding(contentPadding),
            valueActionViewModel = valueActionViewModel,
            volumeViewModel = volumeViewModel
        )
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(actionType, audioStreamType) {
        valueActionViewModel.onActionUpdated(actionType, audioStreamType)
    }

    LaunchedEffect(context) {
        valueActionViewModel.initActivityContext(activity)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            valueActionViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableListenerViewModel.ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                WearableListenerViewModel.EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> {
                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                valueActionViewModel.openPlayStore(activity)

                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    WearableHelper.ActionsPath -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)
                        val action = JSONParser.deserializer(jsonData, Action::class.java)

                        val actionSuccessful = action?.isActionSuccessful ?: false
                        val actionStatus = action?.actionStatus ?: ActionStatus.UNKNOWN

                        if (!actionSuccessful) {
                            lifecycleOwner.lifecycleScope.launch {
                                when (actionStatus) {
                                    ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                        confirmationViewModel.showFailure(
                                            message = context.getString(
                                                R.string.error_actionfailed
                                            )
                                        )
                                    }

                                    ActionStatus.PERMISSION_DENIED -> {
                                        confirmationViewModel.showFailure(
                                            message = context.getString(
                                                R.string.error_permissiondenied
                                            )
                                        )

                                        valueActionViewModel.openAppOnPhone(
                                            activity,
                                            false
                                        )
                                    }

                                    ActionStatus.TIMEOUT -> {
                                        confirmationViewModel.showFailure(
                                            message = context.getString(
                                                R.string.error_sendmessage
                                            )
                                        )
                                    }

                                    ActionStatus.SUCCESS -> {}
                                    else -> {}
                                }
                            }
                        }
                    }

                    WearableHelper.AudioVolumePath, WearableHelper.ValueStatusSetPath -> {
                        val status =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        when (status) {
                            ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                confirmationViewModel.showFailure(message = context.getString(R.string.error_actionfailed))
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                confirmationViewModel.showFailure(message = context.getString(R.string.error_permissiondenied))

                                valueActionViewModel.openAppOnPhone(activity, false)
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Update statuses
        valueActionViewModel.refreshState()
    }
}

@Composable
fun ValueActionScreen(
    modifier: Modifier = Modifier,
    valueActionViewModel: ValueActionViewModel,
    volumeViewModel: ValueActionVolumeViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityCtx = LocalContext.current.findActivity()

    val uiState by valueActionViewModel.uiState.collectAsState()
    val progressUiState by volumeViewModel.volumeUiState.collectAsState()

    ValueActionScreen(
        modifier = modifier
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                focusRequester = rememberFocusRequester(),
                behavior = volumeRotaryBehavior(
                    volumeUiStateProvider = { progressUiState },
                    onRotaryVolumeInput = { newValue -> volumeViewModel.setVolume(newValue) }
                )
            ),
        uiState = uiState,
        volumeUiState = progressUiState,
        onValueChanged = { newValue -> volumeViewModel.setVolume(newValue) },
        onActionChange = {
            valueActionViewModel.requestActionChange()
        }
    )
}

@Composable
fun ValueActionScreen(
    modifier: Modifier = Modifier,
    uiState: ValueActionUiState,
    volumeUiState: VolumeUiState = VolumeUiState(),
    onValueChanged: (Int) -> Unit = {},
    onActionChange: () -> Unit = {}
) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize())
    Stepper(
        value = volumeUiState.current,
        onValueChange = onValueChanged,
        valueProgression = IntProgression.fromClosedRange(
            rangeStart = volumeUiState.min,
            rangeEnd = volumeUiState.max,
            step = if (uiState.action == Actions.VOLUME) {
                1
            } else {
                max(1f, (volumeUiState.max - volumeUiState.min) * 0.05f).toInt()
            }
        ),
        increaseIcon = {
            if (uiState.action == Actions.VOLUME) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_volume_up_white_24dp),
                    contentDescription = stringResource(id = R.string.horologist_volume_screen_volume_up_content_description)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_white_24dp),
                    contentDescription = stringResource(id = R.string.wear_m3c_slider_increase_content_description)
                )
            }
        },
        decreaseIcon = {
            if (uiState.action == Actions.VOLUME) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_volume_down_24),
                    contentDescription = stringResource(id = R.string.horologist_volume_screen_volume_down_content_description)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_remove_white_24dp),
                    contentDescription = stringResource(id = R.string.wear_m3c_slider_decrease_content_description)
                )
            }
        }
    ) {
        Button(
            label = {
                when (uiState.action) {
                    Actions.VOLUME -> {
                        Text(
                            text = stringResource(id = R.string.action_volume),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Actions.BRIGHTNESS -> {
                        Text(
                            text = stringResource(id = R.string.action_brightness),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    else -> {
                        Text(
                            text = stringResource(id = R.string.title_actions),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            icon = {
                when (uiState.action) {
                    Actions.VOLUME -> {
                        Icon(
                            painter = painterResource(
                                id = when (uiState.streamType) {
                                    AudioStreamType.MUSIC -> R.drawable.ic_music_note_white_24dp
                                    AudioStreamType.RINGTONE -> R.drawable.ic_baseline_ring_volume_24dp
                                    AudioStreamType.VOICE_CALL -> R.drawable.ic_baseline_call_24dp
                                    AudioStreamType.ALARM -> R.drawable.ic_alarm_white_24dp
                                    null -> R.drawable.ic_volume_up_white_24dp
                                }
                            ),
                            contentDescription = stringResource(id = R.string.action_volume)
                        )
                    }

                    Actions.BRIGHTNESS -> {
                        Icon(
                            painter = painterResource(
                                id = if (uiState.isAutoBrightnessEnabled) {
                                    R.drawable.ic_brightness_auto
                                } else {
                                    R.drawable.ic_brightness_medium
                                }
                            ),
                            contentDescription = stringResource(id = R.string.action_brightness)
                        )
                    }

                    else -> {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_icon),
                            contentDescription = remember(uiState.action) {
                                uiState.action?.let {
                                    context.getString(
                                        ActionButtonViewModel.getViewModelFromAction(it).actionLabelResId
                                    )
                                }
                            }
                        )
                    }
                }
            },
            onClick = onActionChange
        )
    }
    VolumeLevelIndicator(
        volumeUiState = { volumeUiState }
    )
}

@WearPreviewDevices
@Composable
private fun PreviewValueActionScreen() {
    val state = remember {
        ValueActionUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            action = Actions.VOLUME,
            valueActionState = ValueActionState(50, 0, 100, Actions.VOLUME)
        )
    }

    ValueActionScreen(
        uiState = state
    )
}