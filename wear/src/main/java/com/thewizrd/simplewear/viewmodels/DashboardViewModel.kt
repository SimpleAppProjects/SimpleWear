package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import android.os.CountDownTimer
import android.util.ArrayMap
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.bytesToLong
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

data class DashboardState(
    val connectionStatus: WearConnectionStatus? = null,
    val isStatusLoading: Boolean = false,
    val batteryStatus: BatteryStatus? = null,
    val actions: List<ActionButtonViewModel> = emptyList(),
    val isGridLayout: Boolean = Settings.useGridLayout(),
    val showBatteryState: Boolean = Settings.isShowBatStatus(),
    val isActionsClickable: Boolean = true
)

class DashboardViewModel(app: Application) : WearableListenerViewModel(app) {
    companion object {
        private const val TIMER_SYNC = "key_synctimer"
        private const val TIMER_SYNC_NORESPONSE = "key_synctimer_noresponse"
    }

    private val viewModelState = MutableStateFlow(DashboardState(isStatusLoading = true))
    private var activeTimers: MutableMap<String, CountDownTimer> = ArrayMap()

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    init {
        activeTimers[TIMER_SYNC] = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                viewModelScope.launch {
                    updateConnectionStatus()
                    requestUpdate()
                    startTimer(TIMER_SYNC_NORESPONSE)
                }
            }
        }

        activeTimers[TIMER_SYNC_NORESPONSE] = object : CountDownTimer(2000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}

            override fun onFinish() {
                viewModelScope.launch {
                    activityContext?.let {
                        CustomConfirmationOverlay()
                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                            .setCustomDrawable(
                                ContextCompat.getDrawable(it, R.drawable.ws_full_sad)
                            )
                            .setMessage(it.getString(R.string.error_sendmessage))
                            .showOn(it)
                    }
                }
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

                    WearableHelper.BatteryPath -> {
                        cancelTimer(TIMER_SYNC)
                        cancelTimer(TIMER_SYNC_NORESPONSE)

                        val jsonData = event.data.getString(EXTRA_STATUS)
                        val batStatus = jsonData?.takeIf { it.isNotBlank() }?.let {
                            JSONParser.deserializer(it, BatteryStatus::class.java)
                        }

                        viewModelState.update {
                            it.copy(
                                isStatusLoading = false,
                                batteryStatus = batStatus
                            )
                        }
                    }

                    ACTION_CHANGED -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
                        val action = JSONParser.deserializer(jsonData, Action::class.java)!!

                        requestAction(jsonData)

                        val timer: CountDownTimer = object : CountDownTimer(3000, 500) {
                            override fun onTick(millisUntilFinished: Long) {}

                            override fun onFinish() {
                                action.setActionSuccessful(ActionStatus.TIMEOUT)
                                _eventsFlow.tryEmit(
                                    WearableEvent(
                                        WearableHelper.ActionsPath,
                                        Bundle().apply {
                                            putString(
                                                EXTRA_ACTIONDATA,
                                                JSONParser.serializer(action, Action::class.java)
                                            )
                                        })
                                )
                            }
                        }
                        timer.start()
                        activeTimers[action.actionType.name] = timer

                        // Disable click action for all items until a response is received
                        setActionsClickable(false)
                    }
                }
            }
        }

        resetDashboard()
    }

    fun refreshStatus() {
        viewModelState.update {
            it.copy(
                isStatusLoading = true
            )
        }

        viewModelScope.launch {
            updateConnectionStatus()
            requestUpdate()
            startTimer(TIMER_SYNC)
        }
    }

    fun updateLayout(isGridLayout: Boolean = true) {
        viewModelState.update {
            it.copy(
                isGridLayout = isGridLayout
            )
        }
    }

    fun showBatteryState(show: Boolean = true) {
        viewModelState.update {
            it.copy(
                showBatteryState = show
            )
        }
    }

    fun resetDashboard() {
        val actions = Settings.getDashboardConfig()
        updateActions(actions)
    }

    fun updateButton(action: ActionButtonViewModel) {
        viewModelState.update {
            it.copy(
                actions = it.actions.map { model ->
                    if (model.actionType == action.actionType) {
                        action
                    } else {
                        model
                    }
                }
            )
        }
    }

    fun setActionsClickable(isClickable: Boolean) {
        viewModelState.update {
            it.copy(
                isActionsClickable = isClickable
            )
        }
    }

    fun updateActions(actions: List<Actions>?) {
        resetActions(actions ?: Actions.entries)
    }

    private fun resetActions(actions: List<Actions>) {
        viewModelState.update {
            it.copy(
                actions = actions.map { action ->
                    ActionButtonViewModel.getViewModelFromAction(action)
                }
            )
        }
    }

    fun requestActionChange(action: Action) {
        _eventsFlow.tryEmit(WearableEvent(ACTION_CHANGED, Bundle().apply {
            putString(EXTRA_ACTIONDATA, JSONParser.serializer(action, Action::class.java))
        }))
    }

    fun requestActionStatusUpdate(action: Action) {
        _eventsFlow.tryEmit(WearableEvent(WearableHelper.ActionsPath, Bundle().apply {
            putString(EXTRA_ACTIONDATA, JSONParser.serializer(action, Action::class.java))
        }))
    }

    suspend fun requestPhoneAppVersion(): Long {
        return suspendCancellableCoroutine { continuation ->
            val listener = MessageClient.OnMessageReceivedListener { event ->
                when (event.path) {
                    WearableHelper.VersionPath -> {
                        if (continuation.isActive) {
                            val versionCode = event.data.bytesToLong()
                            continuation.resume(versionCode)
                        }
                    }
                }
            }

            continuation.invokeOnCancellation {
                Wearable.getMessageClient(appContext)
                    .removeListener(listener)
            }

            viewModelScope.launch {
                Wearable.getMessageClient(appContext)
                    .addListener(
                        listener,
                        WearableHelper.getWearDataUri("*", WearableHelper.VersionPath),
                        MessageClient.FILTER_LITERAL
                    ).await()

                if (connect()) {
                    sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.VersionPath, null)
                }
            }
        }
    }

    fun cancelTimer(action: Actions) {
        cancelTimer(action.name, true)
    }

    @Synchronized
    private fun cancelTimer(timerKey: String, remove: Boolean = false) {
        var timer = activeTimers[timerKey]
        if (timer != null) {
            timer.cancel()
            if (remove) {
                activeTimers.remove(timerKey)
                timer = null
            }
        }
    }

    private fun startTimer(timerKey: String) {
        val timer = activeTimers[timerKey]
        timer?.start()
    }
}