package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.data.CallState
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.charToBytes
import com.thewizrd.simplewear.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CallManagerUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val isSpeakerPhoneOn: Boolean = false,
    val isMuted: Boolean = false,

    // InCallUi
    val callerName: String? = null,
    val callerBitmap: Bitmap? = null,
    val callStartTime: Long = -1L,
    val supportsSpeaker: Boolean = false,
    val canSendDTMFKeys: Boolean = false,
    val isCallActive: Boolean = false,
)

class CallManagerViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(CallManagerUiState(isLoading = true))

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
                }
            }
        }
    }

    fun refreshCallState() {
        viewModelState.update {
            it.copy(
                isLoading = true
            )
        }

        viewModelScope.launch {
            updateConnectionStatus()
            requestServiceConnect()
            requestCallState()
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableHelper.LaunchAppPath -> {
                viewModelScope.launch {
                    val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                        putSerializable(EXTRA_STATUS, status)
                    }))
                }
            }

            InCallUIHelper.MuteMicStatusPath -> {
                viewModelState.update {
                    it.copy(
                        isMuted = messageEvent.data.bytesToBool()
                    )
                }
            }

            InCallUIHelper.SpeakerphoneStatusPath -> {
                viewModelState.update {
                    it.copy(
                        isSpeakerPhoneOn = messageEvent.data.bytesToBool()
                    )
                }
            }

            InCallUIHelper.ConnectPath -> {
                viewModelScope.launch {
                    val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    when (status) {
                        ActionStatus.PERMISSION_DENIED -> {
                            viewModelState.update {
                                it.copy(
                                    isLoading = false,
                                    isCallActive = false
                                )
                            }
                        }

                        else -> {}
                    }

                    _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                        putSerializable(EXTRA_STATUS, status)
                    }))
                }
            }

            InCallUIHelper.CallStatePath -> {
                val callState = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), CallState::class.java)
                }

                viewModelScope.launch {
                    updateCallUI(callState)
                }
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private suspend fun updateCallUI(callState: CallState?) {
        val callActive = callState?.callActive ?: false
        val callerName = callState?.callerName
        val callerBmp = callState?.callerBitmap?.toBitmap()
        val callStartTime = callState?.callStartTime ?: -1L
        val inCallFeatures = callState?.supportedFeatures ?: 0
        val supportsSpeakerToggle =
            inCallFeatures and InCallUIHelper.INCALL_FEATURE_SPEAKERPHONE != 0
        val canSendDTMFKey = inCallFeatures and InCallUIHelper.INCALL_FEATURE_DTMF != 0

        viewModelState.update {
            it.copy(
                isLoading = false,
                callerName = callerName?.takeIf { name -> name.isNotBlank() }
                    ?: appContext.getString(R.string.message_callactive),
                callerBitmap = if (callActive) callerBmp else null,
                callStartTime = callStartTime,
                supportsSpeaker = callActive && supportsSpeakerToggle,
                canSendDTMFKeys = callActive && canSendDTMFKey,
                isCallActive = callActive
            )
        }
    }

    private fun requestServiceConnect() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.ConnectPath, null)
            }
        }
    }

    private fun requestCallState() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.CallStatePath, null)
            }
        }
    }

    private fun requestServiceDisconnect() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.DisconnectPath, null)
            }
        }
    }

    fun requestSendDTMFTone(digit: Char) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.DTMFPath, digit.charToBytes())
            }
        }
    }

    fun enableSpeakerphone(enable: Boolean = true) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    InCallUIHelper.SpeakerphonePath,
                    enable.booleanToBytes()
                )
            }
        }
    }

    fun setMuteEnabled(enable: Boolean = true) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    InCallUIHelper.MuteMicPath,
                    enable.booleanToBytes()
                )
            }
        }
    }

    fun endCall() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, InCallUIHelper.EndCallPath, null)
            }
        }
    }

    override fun onCleared() {
        requestServiceDisconnect()
        super.onCleared()
    }
}