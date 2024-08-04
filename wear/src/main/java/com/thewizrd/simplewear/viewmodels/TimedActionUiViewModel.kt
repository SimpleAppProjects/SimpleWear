package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageEvent
import com.google.gson.reflect.TypeToken
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.stringToBytes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TimedActionUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val scheduledActions: Map<Actions, TimedAction> = emptyMap()
)

class TimedActionUiViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(TimedActionUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    val actions = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    ).map {
        it.scheduledActions.map { actions -> actions.value }
    }

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
            sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.TimedActionsStatusPath, null)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            WearableHelper.TimedActionsStatusPath -> {
                val actionMapType = object : TypeToken<Map<Actions, TimedAction>>() {}.type
                val actions = JSONParser.deserializer<Map<Actions, TimedAction>>(
                    messageEvent.data.bytesToString(),
                    actionMapType
                )

                viewModelState.update {
                    it.copy(
                        scheduledActions = actions ?: emptyMap(),
                        isLoading = false
                    )
                }
            }

            WearableHelper.TimedActionAddPath,
            WearableHelper.TimedActionDeletePath,
            WearableHelper.TimedActionUpdatePath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, status)
                }))
            }

            else -> {
                super.onMessageReceived(messageEvent)
            }
        }
    }

    fun requestInitialAction(action: Action) {
        requestAction(action)
    }

    fun requestAddAction(action: TimedAction) {
        viewModelState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.TimedActionAddPath,
                    JSONParser.serializer(action, TimedAction::class.java).stringToBytes()
                )
            }
        }
    }

    fun requestDeleteAction(action: TimedAction) {
        viewModelState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.TimedActionDeletePath,
                    action.action.actionType.name.stringToBytes()
                )
            }
        }
    }

    fun requestUpdateAction(action: TimedAction) {
        viewModelState.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    WearableHelper.TimedActionUpdatePath,
                    JSONParser.serializer(action, TimedAction::class.java).stringToBytes()
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}