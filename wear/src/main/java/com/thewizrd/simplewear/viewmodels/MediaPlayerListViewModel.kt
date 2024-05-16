package com.thewizrd.simplewear.viewmodels

import android.app.Application
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
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.helpers.AppItemComparator
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

data class MediaPlayerListUiState(
    val connectionStatus: WearConnectionStatus? = null,
    internal val allMediaAppsSet: Set<AppItemViewModel> = emptySet(),
    val mediaAppsSet: Set<AppItemViewModel> = emptySet(),
    val filteredAppsList: Set<String> = Settings.getMusicPlayersFilter(),
    val isLoading: Boolean = false,
    val isAutoLaunchEnabled: Boolean = Settings.isAutoLaunchMediaCtrlsEnabled
)

class MediaPlayerListViewModel(app: Application) : WearableListenerViewModel(app),
    OnDataChangedListener {
    private val viewModelState = MutableStateFlow(MediaPlayerListUiState(isLoading = true))

    private val timer: CountDownTimer
    private val mutex = Mutex()

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    private val filteredAppsList = uiState.map { it.filteredAppsList }

    init {
        Wearable.getDataClient(appContext).addListener(this)

        // Set timer for retrieving music player data
        timer = object : CountDownTimer(3000, 1000) {
            override fun onTick(millisUntilFinished: Long) {}
            override fun onFinish() {
                refreshMusicPlayers()
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

        viewModelScope.launch {
            filteredAppsList.collect {
                if (uiState.value.allMediaAppsSet.isNotEmpty()) {
                    updateAppsList()
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            MediaHelper.MusicPlayersPath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (status == ActionStatus.PERMISSION_DENIED) {
                    timer.cancel()

                    viewModelState.update {
                        it.copy(allMediaAppsSet = emptySet())
                    }

                    updateAppsList()
                } else if (status == ActionStatus.SUCCESS) {
                    refreshMusicPlayers()
                }

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, status)
                }))
            }

            MediaHelper.MediaPlayerAutoLaunchPath -> {
                val status = ActionStatus.valueOf(messageEvent.data.bytesToString())

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, status)
                }))
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewModelScope.launch {
            // Cancel timer
            timer.cancel()

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    if (MediaHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)

                            viewModelState.update {
                                it.copy(isLoading = false)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCleared() {
        requestPlayerDisconnect()
        Wearable.getDataClient(appContext).removeListener(this)
        super.onCleared()
    }

    fun refreshState(startTimer: Boolean = false) {
        viewModelScope.launch {
            updateConnectionStatus()
            requestPlayersUpdate()
            if (startTimer) {
                // Wait for music player update
                timer.start()
            }
        }
    }

    suspend fun startMediaApp(item: AppItemViewModel): Boolean {
        return runCatching {
            val intent = MediaHelper.createRemoteActivityIntent(
                item.packageName!!,
                item.activityName!!
            )
            startRemoteActivity(intent)
        }.getOrDefault(false)
    }

    private fun requestPlayersUpdate() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MusicPlayersPath, null)
            }
        }
    }

    private fun requestPlayerDisconnect() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
            }
        }
    }

    private fun refreshMusicPlayers() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MusicPlayersPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MusicPlayersPath == item.uri.path) {
                        try {
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updateMusicPlayers(dataMap)
                        } catch (e: Exception) {
                            Logger.writeLine(Log.ERROR, e)
                        }
                        viewModelState.update {
                            it.copy(isLoading = false)
                        }
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateMusicPlayers(dataMap: DataMap) = mutex.withLock {
        val supportedPlayers =
            dataMap.getStringArrayList(MediaHelper.KEY_SUPPORTEDPLAYERS) ?: return

        val mediaAppsList = mutableSetOf<AppItemViewModel>()

        for (key in supportedPlayers) {
            val map = dataMap.getDataMap(key) ?: continue

            val model = AppItemViewModel().apply {
                appLabel = map.getString(WearableHelper.KEY_LABEL)
                packageName = map.getString(WearableHelper.KEY_PKGNAME)
                activityName = map.getString(WearableHelper.KEY_ACTIVITYNAME)
                bitmapIcon = map.getAsset(WearableHelper.KEY_ICON)?.let {
                    try {
                        ImageUtils.bitmapFromAssetStream(
                            Wearable.getDataClient(appContext),
                            it
                        )
                    } catch (e: Exception) {
                        null
                    }
                }
            }
            mediaAppsList.add(model)
        }

        viewModelState.update {
            it.copy(allMediaAppsSet = mediaAppsList)
        }
        updateAppsList()
    }

    private fun updateAppsList() {
        val filteredApps = Settings.getMusicPlayersFilter()

        if (filteredApps.isEmpty()) {
            viewModelState.update {
                it.copy(
                    mediaAppsSet = it.allMediaAppsSet.toSortedSet(AppItemComparator()),
                    isLoading = false
                )
            }
        } else {
            viewModelState.update { state ->
                state.copy(
                    mediaAppsSet = state.allMediaAppsSet.toMutableList().apply {
                        removeIf { !filteredApps.contains(it.packageName) }
                    }.toSortedSet(AppItemComparator()),
                    isLoading = false
                )
            }
        }
    }

    suspend fun autoLaunchMediaControls() {
        if (Settings.isAutoLaunchMediaCtrlsEnabled) {
            if (connect()) {
                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    MediaHelper.MediaPlayerAutoLaunchPath,
                    null
                )
            }
        }
    }

    fun updateFilteredApps(items: Set<String>) {
        Settings.setMusicPlayersFilter(items)

        viewModelState.update {
            it.copy(filteredAppsList = items)
        }
    }
}