package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageEvent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.GestureActionState
import com.thewizrd.shared_resources.helpers.GestureUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

data class GestureUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val actionState: GestureActionState = GestureActionState()
)

class GestureUiViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(GestureUiState(isLoading = true))

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

    fun refreshState() {
        viewModelState.update {
            it.copy(
                isLoading = true
            )
        }

        viewModelScope.launch {
            updateConnectionStatus()
            requestState()
        }
    }

    private suspend fun requestState() {
        if (connect()) {
            sendMessage(mPhoneNodeWithApp!!.id, GestureUIHelper.GestureStatusPath, null)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            GestureUIHelper.GestureStatusPath -> {
                viewModelScope.launch {
                    val state = JSONParser.deserializer(
                        messageEvent.data.bytesToString(),
                        GestureActionState::class.java
                    ) ?: GestureActionState()
                    val status =
                        if (state.accessibilityEnabled) ActionStatus.SUCCESS else ActionStatus.PERMISSION_DENIED

                    viewModelState.update {
                        it.copy(
                            isLoading = false,
                            actionState = state
                        )
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

    fun requestScroll(dX: Float, dY: Float) {
        viewModelScope.launch {
            if (connect()) {
                val buf = ByteBuffer.allocate(Float.SIZE_BYTES * 2).apply {
                    putFloat(dX)
                    putFloat(dY)
                }
                sendMessage(mPhoneNodeWithApp!!.id, GestureUIHelper.ScrollPath, buf.array())
            }
        }
    }

    fun requestScroll(dX: Float, dY: Float, width: Float, height: Float) {
        viewModelScope.launch {
            if (connect()) {
                val buf = ByteBuffer.allocate(Float.SIZE_BYTES * 4).apply {
                    putFloat(dX)
                    putFloat(dY)
                    putFloat(width)
                    putFloat(height)
                }
                sendMessage(mPhoneNodeWithApp!!.id, GestureUIHelper.ScrollPath, buf.array())
            }
        }
    }

    fun requestDPad(left: Byte = 0, top: Byte = 0, right: Byte = 0, bottom: Byte = 0) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    GestureUIHelper.DPadPath,
                    byteArrayOf(left, top, right, bottom)
                )
            }
        }
    }

    fun requestDPadClick() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, GestureUIHelper.DPadClickPath, null)
            }
        }
    }

    fun requestKeyEvent(key: Int) {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, GestureUIHelper.KeyEventPath, key.intToBytes())
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}