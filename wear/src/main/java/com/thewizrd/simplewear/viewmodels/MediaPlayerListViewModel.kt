package com.thewizrd.simplewear.viewmodels

import android.app.Application
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.ChannelClient.ChannelCallback
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.media.MusicPlayersData
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.helpers.AppItemComparator
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.tasks.await

data class MediaPlayerListUiState(
    val connectionStatus: WearConnectionStatus? = null,
    internal val allMediaAppsSet: Set<AppItemViewModel> = emptySet(),
    val mediaAppsSet: Set<AppItemViewModel> = emptySet(),
    val filteredAppsList: Set<String> = Settings.getMusicPlayersFilter(),
    val activePlayerKey: String? = null,
    val isLoading: Boolean = false
)

class MediaPlayerListViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(MediaPlayerListUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    private val filteredAppsList = uiState.map { it.filteredAppsList }

    private val channelCallback = object : ChannelCallback() {
        override fun onChannelOpened(channel: Channel) {
            startChannelListener(channel)
        }

        override fun onChannelClosed(
            channel: Channel,
            closeReason: Int,
            appSpecificErrorCode: Int
        ) {
            Logger.debug(
                "ChannelCallback",
                "channel closed - reason = $closeReason | path = ${channel.path}"
            )
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

        viewModelScope.launch {
            channelEventsFlow.collect { event ->
                when (event.eventType) {
                    MediaHelper.MusicPlayersPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)

                        viewModelScope.launch {
                            val playersData = jsonData?.let {
                                JSONParser.deserializer(it, MusicPlayersData::class.java)
                            }

                            updateMusicPlayers(playersData)
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
                    viewModelState.update {
                        it.copy(
                            allMediaAppsSet = emptySet(),
                            activePlayerKey = null
                        )
                    }

                    updateAppsList()
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

    private fun startChannelListener(channel: Channel) {
        when (channel.path) {
            MediaHelper.MusicPlayersPath -> {
                createChannelListener(channel)
            }
        }
    }

    private fun createChannelListener(channel: Channel): Job =
        viewModelScope.launch(Dispatchers.Default) {
            supervisorScope {
                runCatching {
                    val stream = Wearable.getChannelClient(appContext)
                        .getInputStream(channel).await()
                    stream.bufferedReader().use { reader ->
                        val line = reader.readLine()

                        when {
                            line.startsWith("data: ") -> {
                                runCatching {
                                    val json = line.substringAfter("data: ")
                                    _channelEventsFlow.tryEmit(
                                        WearableEvent(channel.path, Bundle().apply {
                                            putString(EXTRA_ACTIONDATA, json)
                                        })
                                    )
                                }.onFailure {
                                    Logger.error(
                                        "MediaPlayerListChannelListener",
                                        it,
                                        "error reading data for channel = ${channel.path}"
                                    )
                                }
                            }

                            line.isEmpty() -> {
                                // empty line; data terminator
                            }

                            else -> {}
                        }
                    }
                }.onFailure {
                    Logger.error("MediaPlayerListChannelListener", it)
                }
            }
        }

    override fun onCleared() {
        Wearable.getChannelClient(appContext).run {
            unregisterChannelCallback(channelCallback)
        }
        requestPlayerDisconnect()
        super.onCleared()
    }

    fun refreshState() {
        viewModelScope.launch {
            updateConnectionStatus()
            requestPlayersUpdate()
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

    private suspend fun updateMusicPlayers(playersData: MusicPlayersData?) {
        val mediaAppsList = playersData?.musicPlayers?.mapTo(mutableSetOf()) { player ->
            AppItemViewModel().apply {
                appLabel = player.label
                packageName = player.packageName
                activityName = player.activityName
                bitmapIcon = player.iconBitmap?.toBitmap()
            }
        }

        viewModelState.update {
            it.copy(
                allMediaAppsSet = mediaAppsList ?: emptySet(),
                activePlayerKey = playersData?.activePlayerKey
            )
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

    fun updateFilteredApps(items: Set<String>) {
        Settings.setMusicPlayersFilter(items)

        viewModelState.update {
            it.copy(filteredAppsList = items)
        }
    }
}