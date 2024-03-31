package com.thewizrd.simplewear.viewmodels

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.DataClient.OnDataChangedListener
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.stream.JsonReader
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.AppItemData
import com.thewizrd.shared_resources.helpers.AppItemSerializer
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
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

class AppLauncherViewModel(app: Application) : WearableListenerViewModel(app),
    OnDataChangedListener {
    private val viewModelState = MutableStateFlow(AppLauncherUiState(isLoading = true))

    private val timer: CountDownTimer

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
                                    appsList = createAppsList(items ?: emptyList())
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    init {
        Wearable.getDataClient(appContext).addListener(this)
        Wearable.getChannelClient(appContext).registerChannelCallback(channelCallback)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val buff = Wearable.getDataClient(appContext)
                            .getDataItems(
                                WearableHelper.getWearDataUri(
                                    "*",
                                    WearableHelper.AppsPath
                                )
                            )
                            .await()

                        for (i in 0 until buff.count) {
                            val item = buff[i]
                            if (WearableHelper.AppsPath == item.uri.path) {
                                val appsList = try {
                                    val dataMap = DataMapItem.fromDataItem(item).dataMap
                                    createAppsList(dataMap)
                                } catch (e: Exception) {
                                    Logger.writeLine(Log.ERROR, e)
                                    null
                                }

                                viewModelState.update {
                                    it.copy(
                                        appsList = appsList ?: emptyList(),
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

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewModelScope.launch {
            // Cancel timer
            timer.cancel()

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (WearableHelper.AppsPath == item.uri.path) {
                        val appsList = try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            createAppsList(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                            null
                        }

                        viewModelState.update {
                            it.copy(
                                appsList = appsList ?: emptyList(),
                                isLoading = false
                            )
                        }
                    }
                }
            }
        }
    }

    private fun createAppsList(dataMap: DataMap): List<AppItemViewModel> {
        val availableApps =
            dataMap.getStringArrayList(WearableHelper.KEY_APPS) ?: return emptyList()
        val viewModels = ArrayList<AppItemViewModel>()
        for (key in availableApps) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appType = AppItemViewModel.AppType.APP
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
            }
            viewModels.add(model)
        }

        return viewModels
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

    fun refreshApps(startTimer: Boolean = false) {
        // Update statuses
        viewModelScope.launch {
            updateConnectionStatus()
            requestAppsUpdate()
            if (startTimer) {
                // Wait for apps update
                timer.start()
            }
        }
    }

    fun openRemoteApp(activity: Activity, item: AppItemViewModel) {
        viewModelScope.launch {
            val success = runCatching {
                val intent = WearableHelper.createRemoteActivityIntent(
                    item.packageName!!,
                    item.activityName!!
                )
                startRemoteActivity(intent)
            }.getOrDefault(false)

            activity.showConfirmationOverlay(success)
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
        Wearable.getChannelClient(appContext).unregisterChannelCallback(channelCallback)
        Wearable.getDataClient(appContext).removeListener(this)
        super.onCleared()
    }
}