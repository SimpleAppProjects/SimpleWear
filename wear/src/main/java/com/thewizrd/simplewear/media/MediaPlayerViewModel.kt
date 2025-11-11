@file:OptIn(ExperimentalHorologistApi::class, ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.media

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.ChannelClient.Channel
import com.google.android.gms.wearable.ChannelClient.ChannelCallback
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.model.PlaybackStateEvent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.BrowseMediaItems
import com.thewizrd.shared_resources.media.CustomControls
import com.thewizrd.shared_resources.media.MediaPlayerState
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState
import com.thewizrd.shared_resources.media.QueueItems
import com.thewizrd.shared_resources.utils.ImageUtils.toBitmap
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.shared_resources.utils.intToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.viewmodels.WearableEvent
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
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

data class MediaPlayerUiState(
    val connectionStatus: WearConnectionStatus? = null,
    val isLoading: Boolean = false,
    val isPlaybackLoading: Boolean = false,
    val isPlayerAvailable: Boolean = true,
    val mediaPlayerDetails: AppItemViewModel = AppItemViewModel(),
    val audioStreamState: AudioStreamState? = null,
    // ViewPager pages
    val pagerState: MediaPagerState = MediaPagerState(),
    // Auto-launch
    val isAutoLaunch: Boolean = false,
    // Controls
    val playerState: PlayerState = PlayerState(),
    // Custom Controls
    val mediaCustomItems: List<MediaItemModel> = emptyList(),
    // Media Browser
    val mediaBrowserItems: List<MediaItemModel> = emptyList(),
    // Media Queue
    val mediaQueueItems: List<MediaItemModel> = emptyList(),
    val activeQueueItemId: Long = -1
)

enum class MediaPageType(val value: Int) {
    Player(1),
    CustomControls(2),
    Browser(3),
    Queue(4);

    companion object {
        fun valueOf(value: Int) = entries.firstOrNull { it.value == value }
    }
}

data class PlayerState(
    val playbackState: PlaybackState = PlaybackState.NONE,
    val title: String? = null,
    val artist: String? = null,
    val artworkBitmap: Bitmap? = null,
    val positionState: PositionState? = null
) {
    fun isEmpty(): Boolean = title.isNullOrEmpty() && artist.isNullOrEmpty()

    val key = "${title}|${artist}"
}

data class MediaPagerState(
    val supportsBrowser: Boolean = false,
    val supportsCustomActions: Boolean = false,
    val supportsQueue: Boolean = false,
    val currentPageKey: MediaPageType = MediaPageType.Player
) {
    val pageCount: Int
        get() {
            var pageCount = 1

            if (supportsCustomActions) pageCount++
            if (supportsBrowser) pageCount++
            if (supportsQueue) pageCount++

            return pageCount
        }
}

class MediaPlayerViewModel(app: Application) : WearableListenerViewModel(app) {
    private val viewModelState = MutableStateFlow(MediaPlayerUiState(isLoading = true))

    val uiState = viewModelState.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value
    )

    val playerState = viewModelState.map { it.playerState }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        viewModelState.value.playerState
    )

    @OptIn(ExperimentalHorologistApi::class)
    val playbackStateEvent = viewModelState.map { it.playerState.toPlaybackStateEvent() }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        PlaybackStateEvent.INITIAL
    )

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

                    MediaHelper.MediaBrowserItemsBackPath -> {
                        viewModelScope.launch {
                            if (connect()) {
                                val state = uiState.value

                                mPhoneNodeWithApp?.id?.let { nodeID ->
                                    sendMessage(
                                        nodeID,
                                        MediaHelper.MediaPlayerConnectPath,
                                        if (state.isAutoLaunch) state.isAutoLaunch.booleanToBytes() else state.mediaPlayerDetails.packageName?.stringToBytes()
                                    )
                                    sendMessage(nodeID, event.eventType, null)
                                }
                            }
                        }
                    }

                    MediaHelper.MediaBrowserItemsClickPath,
                    MediaHelper.MediaBrowserItemsExtraSuggestedClickPath,
                    MediaHelper.MediaQueueItemsClickPath -> {
                        val id = event.data.getString(MediaHelper.KEY_MEDIAITEM_ID)

                        viewModelScope.launch {
                            if (connect()) {
                                val state = uiState.value

                                mPhoneNodeWithApp?.id?.let { nodeID ->
                                    sendMessage(
                                        nodeID,
                                        MediaHelper.MediaPlayerConnectPath,
                                        if (state.isAutoLaunch) state.isAutoLaunch.booleanToBytes() else state.mediaPlayerDetails.packageName?.stringToBytes()
                                    )
                                    sendMessage(
                                        nodeID,
                                        event.eventType,
                                        id!!.stringToBytes()
                                    )
                                }
                            }
                        }
                    }

                    ACTION_CHANGED -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
                        requestAction(jsonData)
                    }
                }
            }
        }

        viewModelScope.launch {
            channelEventsFlow.collect { event ->
                when (event.eventType) {
                    MediaHelper.MediaActionsPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)

                        viewModelScope.launch {
                            val customControls = jsonData?.let {
                                JSONParser.deserializer(it, CustomControls::class.java)
                            }

                            updateCustomControls(customControls)
                        }
                    }

                    MediaHelper.MediaBrowserItemsPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)

                        viewModelScope.launch {
                            val browseMediaItems = jsonData?.let {
                                JSONParser.deserializer(it, BrowseMediaItems::class.java)
                            }

                            updateBrowserItems(browseMediaItems)
                        }
                    }
//                    MediaHelper.MediaBrowserItemsExtraSuggestedPath -> {
//                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
//
//                        viewModelScope.launch {
//                            val browseMediaItems = jsonData?.let {
//                                JSONParser.deserializer(it, BrowseMediaItems::class.java)
//                            }
//
//                            updateBrowserItems(browseMediaItems)
//                        }
//                    }
                    MediaHelper.MediaQueueItemsPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)

                        viewModelScope.launch {
                            val queueItems = jsonData?.let {
                                JSONParser.deserializer(it, QueueItems::class.java)
                            }

                            updateQueueItems(queueItems)
                        }
                    }
                }
            }
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            MediaHelper.MediaPlayerConnectPath,
            MediaHelper.MediaPlayerAutoLaunchPath -> {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())

                if (actionStatus == ActionStatus.PERMISSION_DENIED) {
                    viewModelState.update {
                        it.copy(isPlayerAvailable = false)
                    }
                } else if (actionStatus == ActionStatus.SUCCESS) {
                    viewModelState.update {
                        it.copy(isPlayerAvailable = true)
                    }
                }

                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, actionStatus)
                }))
            }

            MediaHelper.MediaBrowserItemsClickPath,
            MediaHelper.MediaBrowserItemsExtraSuggestedClickPath -> {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())
                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, actionStatus)
                }))
            }

            WearableHelper.AudioStatusPath,
            MediaHelper.MediaVolumeStatusPath -> {
                val status = messageEvent.data?.let {
                    JSONParser.deserializer(
                        it.bytesToString(),
                        AudioStreamState::class.java
                    )
                }

                viewModelState.update {
                    it.copy(audioStreamState = status)
                }
            }

            MediaHelper.MediaPlayPath -> {
                val actionStatus = ActionStatus.valueOf(messageEvent.data.bytesToString())
                _eventsFlow.tryEmit(WearableEvent(messageEvent.path, Bundle().apply {
                    putSerializable(EXTRA_STATUS, actionStatus)
                }))
            }

            MediaHelper.MediaPlayerStatePath -> {
                val playerState = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), MediaPlayerState::class.java)
                }

                viewModelScope.launch {
                    updatePlayerState(playerState)
                }
            }

            MediaHelper.MediaPlayerArtPath -> {
                val artworkBytes = messageEvent.data

                viewModelScope.launch {
                    updatePlayerArtwork(artworkBytes)
                }
            }

            MediaHelper.MediaPlayerAppInfoPath -> {
                val appInfo = messageEvent.data?.let {
                    JSONParser.deserializer(it.bytesToString(), AppItemData::class.java)
                }

                viewModelScope.launch {
                    viewModelState.update {
                        it.copy(
                            mediaPlayerDetails = AppItemViewModel().apply {
                                appLabel = appInfo?.label
                                packageName = appInfo?.packageName
                                activityName = appInfo?.activityName
                                bitmapIcon = appInfo?.iconBitmap?.toBitmap()
                            }
                        )
                    }
                }
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private fun startChannelListener(channel: Channel) {
        when (channel.path) {
            MediaHelper.MediaActionsPath,
            MediaHelper.MediaBrowserItemsPath,
            MediaHelper.MediaBrowserItemsExtraSuggestedPath,
            MediaHelper.MediaQueueItemsPath -> {
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
                                        "MediaPlayerChannelListener",
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
                    Logger.error("MediaPlayerChannelListener", it)
            }
        }
    }

    fun autoLaunch() {
        viewModelState.update {
            it.copy(
                mediaPlayerDetails = AppItemViewModel(),
                isAutoLaunch = true
            )
        }
        requestPlayerConnect()
    }

    fun updateMediaPlayerDetails(player: AppItemViewModel) {
        viewModelState.update {
            it.copy(
                mediaPlayerDetails = player,
                isAutoLaunch = false
            )
        }
        requestPlayerConnect()
    }

    private fun requestPlayerConnect() {
        viewModelScope.launch {
            if (connect()) {
                val state = uiState.value

                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    MediaHelper.MediaPlayerConnectPath,
                    if (state.isAutoLaunch) true.booleanToBytes() else state.mediaPlayerDetails.packageName?.stringToBytes()
                )
            }
        }
    }

    fun requestPlayerDisconnect() {
        viewModelScope.launch {
            if (connect()) {
                sendMessage(mPhoneNodeWithApp!!.id, MediaHelper.MediaPlayerDisconnectPath, null)
            }
        }
    }

    private fun requestPlayerAppInfo() {
        requestMediaAction(MediaHelper.MediaPlayerAppInfoPath)
    }

    fun refreshStatus() {
        viewModelScope.launch {
            updateConnectionStatus()
            requestPlayerConnect()
            requestPlayerAppInfo()
            requestUpdateCustomControls()
            //requestUpdateBrowserItems()
            requestUpdateQueueItems()
        }
    }

    fun refreshPlayerState() {
        viewModelScope.launch {
            // Request connect to media player
            requestVolumeStatus()
            requestUpdatePlayerState()
        }
    }

    fun requestUpdatePlayerState() {
        requestMediaAction(MediaHelper.MediaPlayerStatePath)
    }

    private suspend fun updatePlayerState(playerState: MediaPlayerState?) {
        val playbackState = playerState?.playbackState ?: PlaybackState.NONE
        val title = playerState?.mediaMetaData?.title
        val artist = playerState?.mediaMetaData?.artist
        val positionState = playerState?.mediaMetaData?.positionState

        if (playbackState != PlaybackState.NONE) {
            viewModelState.update {
                it.copy(
                    playerState = it.playerState.copy(
                        playbackState = playbackState,
                        title = title,
                        artist = artist,
                        positionState = positionState
                    ),
                    isLoading = false,
                    isPlaybackLoading = playbackState == PlaybackState.LOADING
                )
            }
        } else {
            viewModelState.update {
                it.copy(
                    playerState = PlayerState(),
                    isLoading = false,
                    isPlaybackLoading = false
                )
            }
        }
    }

    private suspend fun updatePlayerArtwork(artworkBytes: ByteArray?) {
        val artworkBitmap = artworkBytes?.toBitmap()

        viewModelState.update {
            it.copy(
                playerState = it.playerState.copy(
                    artworkBitmap = artworkBitmap
                )
            )
        }
    }

    fun requestPlayPauseAction(play: Boolean = true) {
        requestMediaAction(if (play) MediaHelper.MediaPlayPath else MediaHelper.MediaPausePath)
    }

    fun requestSkipToPreviousAction() {
        requestMediaAction(MediaHelper.MediaPreviousPath)
    }

    fun requestSkipToNextAction() {
        requestMediaAction(MediaHelper.MediaNextPath)
    }

    fun requestVolumeUp() {
        requestMediaAction(MediaHelper.MediaVolumeUpPath)
    }

    fun requestVolumeDown() {
        requestMediaAction(MediaHelper.MediaVolumeDownPath)
    }

    fun requestVolumeStatus() {
        requestMediaAction(MediaHelper.MediaVolumeStatusPath)
    }

    fun requestSetVolume(value: Int) {
        requestMediaAction(MediaHelper.MediaSetVolumePath, value.intToBytes())
    }

    private fun requestMediaAction(path: String, data: ByteArray? = null) {
        viewModelScope.launch {
            if (connect()) {
                val state = uiState.value

                mPhoneNodeWithApp?.id?.let { nodeID ->
                    sendMessage(
                        nodeID,
                        MediaHelper.MediaPlayerConnectPath,
                        if (state.isAutoLaunch) state.isAutoLaunch.booleanToBytes() else state.mediaPlayerDetails.packageName?.stringToBytes()
                    )
                    sendMessage(nodeID, path, data)
                }
            }
        }
    }

    // Custom Controls
    fun requestCustomMediaActionItem(itemId: String) {
        requestMediaAction(MediaHelper.MediaActionsClickPath, itemId.stringToBytes())
    }

    fun requestUpdateCustomControls() {
        requestMediaAction(MediaHelper.MediaActionsPath)
    }

    private suspend fun updateCustomControls(customControls: CustomControls?) {
        val mediaItems = customControls?.actions?.map { action ->
            MediaItemModel(action.action).apply {
                title = action.title
                icon = action.icon?.toBitmap()
            }
        }

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaCustomItems = mediaItems ?: emptyList(),
                pagerState = it.pagerState.copy(
                    supportsCustomActions = !mediaItems.isNullOrEmpty()
                )
            )
        }
    }

    // Media Browser
    fun requestUpdateBrowserItems() {
        requestMediaAction(MediaHelper.MediaBrowserItemsPath)
    }

    private suspend fun updateBrowserItems(browseMediaItems: BrowseMediaItems?) {
        val isRoot = browseMediaItems?.isRoot ?: true
        val items = browseMediaItems?.mediaItems ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(if (isRoot) items.size else items.size + 1)
        if (!isRoot) {
            mediaItems.add(MediaItemModel(MediaHelper.ACTIONITEM_BACK))
        }

        for (item in items) {
            mediaItems.add(MediaItemModel(item.mediaId).apply {
                this.icon = item.icon?.toBitmap()
                this.title = item.title
            })
        }

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaBrowserItems = mediaItems,
                pagerState = it.pagerState.copy(
                    supportsBrowser = items.isNotEmpty()
                )
            )
        }
    }

    fun requestBrowserActionItem(itemId: String) {
        if (itemId == MediaHelper.ACTIONITEM_BACK) {
            requestMediaAction(MediaHelper.MediaBrowserItemsBackPath)
        } else {
            requestMediaAction(MediaHelper.MediaBrowserItemsClickPath, itemId.stringToBytes())
        }
    }

    // Media Queue
    fun requestUpdateQueueItems() {
        requestMediaAction(MediaHelper.MediaQueueItemsPath)
    }

    private suspend fun updateQueueItems(queueItems: QueueItems?) {
        val mediaQueueItems = queueItems?.queueItems?.map { item ->
            MediaItemModel(item.queueId.toString()).apply {
                this.icon = item.icon?.toBitmap()
                this.title = item.title
                this.subTitle = item.subTitle
            }
        }

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaQueueItems = mediaQueueItems ?: emptyList(),
                activeQueueItemId = queueItems?.activeQueueItemId ?: -1,
                pagerState = it.pagerState.copy(
                    supportsQueue = !mediaQueueItems.isNullOrEmpty()
                )
            )
        }
    }

    fun requestQueueActionItem(itemId: String) {
        requestMediaAction(MediaHelper.MediaQueueItemsClickPath, itemId.stringToBytes())
    }

    fun updateCurrentPage(pageType: MediaPageType) {
        viewModelState.update {
            it.copy(
                pagerState = it.pagerState.copy(
                    currentPageKey = pageType
                )
            )
        }
    }

    override fun onCleared() {
        Wearable.getChannelClient(appContext).run {
            unregisterChannelCallback(channelCallback)
        }
        super.onCleared()
    }
}