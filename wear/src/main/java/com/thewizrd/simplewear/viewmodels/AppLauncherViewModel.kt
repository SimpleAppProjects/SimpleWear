package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.stream.JsonReader
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.data.AppItemSerializer
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.InputStreamReader

data class AppLauncherUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val appsList: List<AppItemViewModel> = emptyList(),
    val isLoading: Boolean = false,
    val loadAppIcons: Boolean = Settings.isLoadAppIcons()
)

class AppLauncherViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(AppLauncherUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    private val channelCallback = object : ChannelClient.ChannelCallback() {
        override fun onChannelOpened(channel: ChannelClient.Channel) {
            super.onChannelOpened(channel)
            // Check if we can load the data
            if (channel.path == WearableHelper.AppsPath) {
                viewModelScope.launch(Dispatchers.IO) {
                    val channelClient = Wearable.getChannelClient(appContext)
                    runCatching {
                        val inputStream = channelClient.getInputStream(channel).await()
                        inputStream.use {
                            val reader = JsonReader(InputStreamReader(it))
                            val items = AppItemSerializer.deserialize(reader)

                            viewModelState.update { state ->
                                state.copy(
                                    appsList = createAppsList(items),
                                    isLoading = false
                                )
                            }
                        }
                    }.onFailure {
                        Logger.writeLine(Log.ERROR, it)
                    }
                }
            }
        }
    }

    init {
        Wearable.getChannelClient(appContext).run {
            registerChannelCallback(channelCallback)
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

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearableHelper.LaunchAppPath) {
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

    private suspend fun createAppsList(items: List<AppItemData>): List<AppItemViewModel> {
        val viewModels = ArrayList<AppItemViewModel>(items.size)

        items.forEach { item ->
            val model = AppItemViewModel().apply {
                appType = AppItemViewModel.AppType.APP
                appLabel = item.label
                packageName = item.packageName
                activityName = item.activityName
                bitmapIcon = item.iconBitmap?.toBitmap()
            }
            viewModels.add(model)
        }

        return viewModels
    }

    fun showAppIcons(show: Boolean = true) {
        Settings.setLoadAppIcons(show)

        viewModelScope.launch(Dispatchers.IO) {
            val dataRequest = PutDataMapRequest.create(WearableHelper.AppsIconSettingsPath)
            dataRequest.dataMap.putBoolean(WearableHelper.KEY_ICON, show)
            dataRequest.setUrgent()
            runCatching {
                Wearable
                    .getDataClient(appContext)
                    .putDataItem(dataRequest.asPutDataRequest())
                    .await()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
        }

        viewModelState.update {
            it.copy(
                loadAppIcons = show
            )
        }
    }

    fun refreshApps() {
        // Update statuses
        viewModelScope.launch {
            updateConnectionStatus()
            requestAppsUpdate()
        }
    }

    fun openRemoteApp(item: AppItemViewModel) {
        viewModelScope.launch {
            val success = runCatching {
                val intent = WearableHelper.createRemoteActivityIntent(
                    item.packageName!!,
                    item.activityName!!
                )
                startRemoteActivity(intent)
            }.getOrDefault(false)

            _eventsFlow.tryEmit(
                WearableEvent(
                    ACTION_SHOWCONFIRMATION,
                    Bundle().apply {
                        putString(
                            EXTRA_ACTIONDATA,
                            JSONParser.serializer(
                                ConfirmationData(
                                    confirmationType = if (success) ConfirmationType.OpenOnPhone else ConfirmationType.Failure
                                ), ConfirmationData::class.java
                            )
                        )
                    }
                )
            )
        }
    }

    private fun requestAppsUpdate() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, WearableHelper.AppsPath, null)
            }
        }
    }

    override fun onCleared() {
        Wearable.getChannelClient(appContext).run {
            unregisterChannelCallback(channelCallback)
        }
        super.onCleared()
    }
}