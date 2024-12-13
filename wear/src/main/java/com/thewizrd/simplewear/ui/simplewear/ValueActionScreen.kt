package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Stepper
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.audio.ui.VolumeUiState
import com.google.android.horologist.audio.ui.rotaryVolumeControlsWithFocus
import com.google.android.horologist.compose.rotaryinput.RotaryDefaults
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.ValueActionUiState
import com.thewizrd.simplewear.viewmodels.ValueActionViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

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

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = {
            TimeText()
        },
    ) {
        ValueActionScreen(valueActionViewModel)
    }

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
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    activity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(activity.getString(R.string.error_actionfailed))
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

                                        valueActionViewModel.openAppOnPhone(
                                            activity,
                                            false
                                        )
                                    }

                                    ActionStatus.TIMEOUT -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    activity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(activity.getString(R.string.error_sendmessage))
                                            .showOn(activity)
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
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            activity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(activity.getString(R.string.error_actionfailed))
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

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun ValueActionScreen(
    valueActionViewModel: ValueActionViewModel
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activityCtx = LocalContext.current.findActivity()

    val uiState by valueActionViewModel.uiState.collectAsState()
    val progressUiState by valueActionViewModel.uiState.map {
        VolumeUiState(
            current = it.valueActionState?.currentValue ?: 0,
            min = it.valueActionState?.minValue ?: 0,
            max = it.valueActionState?.maxValue ?: 1
        )
    }.collectAsState(VolumeUiState())

    ValueActionScreen(
        modifier = Modifier.rotaryVolumeControlsWithFocus(
            volumeUiStateProvider = {
                progressUiState
            },
            onRotaryVolumeInput = {
                if (it > (uiState.valueActionState?.currentValue ?: 0)) {
                    valueActionViewModel.increaseValue()
                } else {
                    valueActionViewModel.decreaseValue()
                }
            },
            localView = LocalView.current,
            isLowRes = RotaryDefaults.isLowResInput()
        ),
        uiState = uiState,
        onValueChanged = {
            if (it > (uiState.valueActionState?.currentValue ?: 0)) {
                valueActionViewModel.increaseValue()
            } else {
                valueActionViewModel.decreaseValue()
            }
        },
        onActionChange = {
            valueActionViewModel.requestActionChange()
        }
    )
}

@Composable
fun ValueActionScreen(
    modifier: Modifier = Modifier,
    uiState: ValueActionUiState,
    onValueChanged: (Int) -> Unit = {},
    onActionChange: () -> Unit = {}
) {
    Box(modifier = modifier.fillMaxSize())
    Stepper(
        value = uiState.valueActionState?.currentValue ?: 0,
        onValueChange = onValueChanged,
        valueProgression = IntProgression.fromClosedRange(
            rangeStart = uiState.valueActionState?.minValue ?: 0,
            rangeEnd = uiState.valueActionState?.maxValue ?: 100,
            step = 1
        ),
        increaseIcon = {
            if (uiState.action == Actions.VOLUME) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_volume_up_white_24dp),
                    contentDescription = stringResource(id = R.string.horologist_stepper_increase_content_description)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add_white_24dp),
                    contentDescription = stringResource(id = R.string.horologist_stepper_increase_content_description)
                )
            }
        },
        decreaseIcon = {
            if (uiState.action == Actions.VOLUME) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_baseline_volume_down_24),
                    contentDescription = stringResource(id = R.string.horologist_stepper_decrease_content_description)
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.ic_remove_white_24dp),
                    contentDescription = stringResource(id = R.string.horologist_stepper_decrease_content_description)
                )
            }
        }
    ) {
        Chip(
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
                            contentDescription = null
                        )
                    }
                }
            },
            colors = ChipDefaults.secondaryChipColors(),
            onClick = onActionChange
        )
    }
    PositionIndicator(
        value = {
            uiState.valueActionState?.currentValue?.toFloat() ?: 0f
        },
        range = (uiState.valueActionState?.minValue?.toFloat()
            ?: 0f)..(uiState.valueActionState?.maxValue?.toFloat() ?: 1f),
        color = MaterialTheme.colors.primary
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