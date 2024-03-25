package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import android.os.CountDownTimer
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.ValueAction
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.actions.ValueDirection
import com.thewizrd.shared_resources.actions.VolumeAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ValueActionUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val action: Actions? = null,
    val remoteValue: Float? = null,
    val valueActionState: ValueActionState? = null,
    val streamType: AudioStreamType? = AudioStreamType.MUSIC,
    val isAutoBrightnessEnabled: Boolean = true
)

class ValueActionViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(ValueActionUiState())

    private var timer: CountDownTimer? = null

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    init {
        viewModelScope.launch {
            eventFlow.collect { event ->
                when (event.eventType) {
                    ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        viewModelState.update {
                            it.copy(
                                connectionStatus = connectionStatus
                            )
                        }
                    }

                    WearableHelper.ActionsPath -> {
                        timer?.cancel()
                    }

                    ACTION_CHANGED -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
                        val action = JSONParser.deserializer(jsonData, Action::class.java)!!

                        requestAction(jsonData)

                        viewModelScope.launch {
                            timer?.cancel()
                            timer = object : CountDownTimer(3000, 500) {
                                override fun onTick(millisUntilFinished: Long) {}

                                override fun onFinish() {
                                    action.setActionSuccessful(ActionStatus.TIMEOUT)
                                    _eventsFlow.tryEmit(
                                        WearableEvent(
                                            WearableHelper.ActionsPath,
                                            Bundle().apply {
                                                putString(
                                                    EXTRA_ACTIONDATA,
                                                    JSONParser.serializer(
                                                        action,
                                                        Action::class.java
                                                    )
                                                )
                                            }
                                        )
                                    )
                                }
                            }
                            timer!!.start()
                        }
                    }
                }
            }
        }
    }

    fun refreshState() {
        viewModelScope.launch {
            updateConnectionStatus()
            when (uiState.value.action) {
                Actions.VOLUME -> {
                    requestAudioStreamState()
                }

                else -> {
                    requestValueState()
                }
            }
        }
    }

    fun onActionUpdated(action: Actions, streamType: AudioStreamType? = null) {
        viewModelState.update {
            it.copy(
                action = action,
                streamType = streamType ?: it.streamType
            )
        }
    }

    fun increaseValue() {
        val state = uiState.value

        val actionData = if (state.action == Actions.VOLUME && state.streamType != null) {
            VolumeAction(ValueDirection.UP, state.streamType)
        } else {
            ValueAction(state.action!!, ValueDirection.UP)
        }

        _eventsFlow.tryEmit(WearableEvent(ACTION_CHANGED, Bundle().apply {
            putString(EXTRA_ACTIONDATA, JSONParser.serializer(actionData, Action::class.java))
        }))
    }

    fun decreaseValue() {
        val state = uiState.value

        val actionData = if (state.action == Actions.VOLUME && state.streamType != null) {
            VolumeAction(ValueDirection.DOWN, state.streamType)
        } else {
            ValueAction(state.action!!, ValueDirection.DOWN)
        }

        _eventsFlow.tryEmit(WearableEvent(ACTION_CHANGED, Bundle().apply {
            putString(EXTRA_ACTIONDATA, JSONParser.serializer(actionData, Action::class.java))
        }))
    }

    fun requestActionChange() {
        val state = uiState.value

        if (state.streamType != null && state.action == Actions.VOLUME) {
            viewModelState.update {
                val maxStates = AudioStreamType.entries.size
                var newValue = (state.streamType.value + 1) % maxStates
                if (newValue < 0) newValue += maxStates

                it.copy(streamType = AudioStreamType.valueOf(newValue))
            }

            viewModelScope.launch {
                requestAudioStreamState()
            }
        } else if (state.action == Actions.BRIGHTNESS) {
            viewModelScope.launch {
                requestToggleAutoBrightness()
            }
        }
    }

    private suspend fun requestAudioStreamState() {
        val state = uiState.value

        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.AudioStatusPath,
                state.streamType?.name?.stringToBytes()
            )
        }
    }

    private suspend fun requestValueState() {
        val state = uiState.value

        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.ValueStatusPath,
                state.action?.value?.intToBytes()
            )
        }
    }

    private suspend fun requestSetVolume(value: Int) {
        val state = uiState.value

        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.AudioVolumePath,
                (state.valueActionState as? AudioStreamState)?.let {
                    JSONParser.serializer(
                        AudioStreamState(value, it.minVolume, it.maxVolume, it.streamType),
                        AudioStreamState::class.java
                    ).stringToBytes()
                }
            )
        }
    }

    private suspend fun requestSetValue(value: Int) {
        val state = uiState.value

        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.ValueStatusSetPath,
                state.valueActionState?.let {
                    JSONParser.serializer(
                        ValueActionState(value, it.minValue, it.maxValue, it.actionType),
                        ValueActionState::class.java
                    ).stringToBytes()
                }
            )
        }
    }

    private suspend fun requestToggleAutoBrightness() {
        if (connect()) {
            sendMessage(
                mPhoneNodeWithApp!!.id,
                WearableHelper.BrightnessModePath,
                null
            )
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val state = uiState.value

        if (messageEvent.path == WearableHelper.AudioStatusPath && state.action == Actions.VOLUME) {
            viewModelScope.launch {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), AudioStreamState::class.java)
                }

                viewModelState.update {
                    it.copy(
                        valueActionState = status,
                        streamType = status?.streamType
                    )
                }
            }
        } else if (messageEvent.path == WearableHelper.ValueStatusPath) {
            viewModelScope.launch {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), ValueActionState::class.java)
                }

                viewModelState.update {
                    it.copy(
                        valueActionState = status
                    )
                }
            }
        } else if (messageEvent.path == WearableHelper.BrightnessModePath && state.action == Actions.BRIGHTNESS) {
            viewModelScope.launch {
                val enabled = messageEvent.data.bytesToBool()
                viewModelState.update {
                    it.copy(
                        isAutoBrightnessEnabled = enabled
                    )
                }
            }
        } else if (messageEvent.path == WearableHelper.AudioVolumePath || messageEvent.path == WearableHelper.ValueStatusSetPath) {
            viewModelScope.launch {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, status)
                }))
            }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }
}