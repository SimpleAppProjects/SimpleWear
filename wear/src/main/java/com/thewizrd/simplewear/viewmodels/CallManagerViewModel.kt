package com.thewizrd.simplewear.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.charToBytes
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ValueActionActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CallManagerUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val isSpeakerPhoneOn: Boolean = false,
    val isMuted: Boolean = false,

    // InCallUi
    val callerName: String? = null,
    val callerBitmap: Bitmap? = null,
    val supportsSpeaker: Boolean = false,
    val canSendDTMFKeys: Boolean = false,
    val isCallActive: Boolean = false,
)

class CallManagerViewModel(app: Application) : WearableListenerViewModel(app),
    OnDataChangedListener {
    private val viewModelState = MutableStateFlow(CallManagerUiState(isLoading = true))

    private val timer: CountDownTimer

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    init {
        Wearable.getDataClient(appContext).addListener(this)

        // Set timer for retrieving call status
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                refreshCallUI()
            }
        }

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
            requestCallState()
            timer.start()
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

            InCallUIHelper.CallStatePath -> {
                viewModelScope.launch {
                    val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                    when (status) {
                        ActionStatus.PERMISSION_DENIED -> {
                            timer.cancel()

                            viewModelState.update {
                                it.copy(
                                    isLoading = false,
                                    isCallActive = false
                                )
                            }
                        }

                        ActionStatus.SUCCESS -> refreshCallUI()
                        else -> {}
                    }

                    _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                        putSerializable(EXTRA_STATUS, status)
                    }))
                }
            }

            else -> {
                super.onMessageReceived(messageEvent)
            }
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewModelScope.launch {
            viewModelState.update {
                it.copy(
                    isLoading = false
                )
            }

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (InCallUIHelper.CallStatePath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateCallUI(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                    }
                }
            }
        }
    }

    private suspend fun updateCallUI(dataMap: DataMap) {
        val callActive = dataMap.getBoolean(InCallUIHelper.KEY_CALLACTIVE, false)
        val callerName = dataMap.getString(InCallUIHelper.KEY_CALLERNAME)
        val callerBmp = dataMap.getAsset(InCallUIHelper.KEY_CALLERBMP)?.let {
            try {
                ImageUtils.bitmapFromAssetStream(
                    Wearable.getDataClient(appContext),
                    it
                )
            } catch (e: Exception) {
                null
            }
        }
        val inCallFeatures = dataMap.getInt(InCallUIHelper.KEY_SUPPORTEDFEATURES)
        val supportsSpeakerToggle =
            inCallFeatures and InCallUIHelper.INCALL_FEATURE_SPEAKERPHONE != 0
        val canSendDTMFKey = inCallFeatures and InCallUIHelper.INCALL_FEATURE_DTMF != 0

        viewModelState.update {
            it.copy(
                callerName = callerName?.takeIf { it.isNotBlank() }
                    ?: appContext.getString(R.string.message_callactive),
                callerBitmap = if (callActive) callerBmp else null,
                supportsSpeaker = callActive && supportsSpeakerToggle,
                canSendDTMFKeys = callActive && canSendDTMFKey,
                isCallActive = callActive
            )
        }
    }

    private fun refreshCallUI() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            InCallUIHelper.CallStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (InCallUIHelper.CallStatePath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateCallUI(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                        viewModelState.update {
                            it.copy(
                                isLoading = false
                            )
                        }
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
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

    fun showCallVolumeActivity(activityContext: Activity) {
        val intent: Intent = Intent(activityContext, ValueActionActivity::class.java)
            .putExtra(EXTRA_ACTION, Actions.VOLUME)
            .putExtra(ValueActionActivity.EXTRA_STREAMTYPE, AudioStreamType.VOICE_CALL)
        activityContext.startActivityForResult(intent, -1)
    }

    override fun onCleared() {
        requestServiceDisconnect()
        Wearable.getDataClient(appContext).removeListener(this)
        super.onCleared()
    }
}