package com.thewizrd.simplewear.media

import android.app.Application
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.media.model.PlaybackStateEvent
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.PlaybackState
import com.thewizrd.shared_resources.media.PositionState
import com.thewizrd.shared_resources.utils.ImageUtils
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
}

data class MediaPagerState(
    val supportsBrowser: Boolean = false,
    val supportsCustomActions: Boolean = false,
    val supportsQueue: Boolean = false
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

class MediaPlayerViewModel(app: Application) : WearableListenerViewModel(app),
    DataClient.OnDataChangedListener {
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

    private var deleteJob: Job? = null

    private var mediaPagerState = MediaPagerState()
    private var updatePagerJob: Job? = null

    init {
        Wearable.getDataClient(appContext).addListener(this)

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

            else -> super.onMessageReceived(messageEvent)
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        viewModelScope.launch {
            updatePagerJob?.cancel()
            var isPagerUpdated = false

            for (event in dataEventBuffer) {
                if (event.type == DataEvent.TYPE_CHANGED) {
                    val item = event.dataItem
                    when (item.uri.path) {
                        MediaHelper.MediaActionsPath -> {
                            try {
                                updatePager(item)
                                val dataMap = DataMapItem.fromDataItem(item).dataMap
                                updateCustomControls(dataMap)
                                isPagerUpdated = true
                            } catch (e: Exception) {
                                Logger.writeLine(Log.ERROR, e)
                            }
                        }

                        MediaHelper.MediaBrowserItemsPath -> {
                            try {
                                updatePager(item)
                                val dataMap = DataMapItem.fromDataItem(item).dataMap
                                updateBrowserItems(dataMap)
                                isPagerUpdated = true
                            } catch (e: Exception) {
                                Logger.writeLine(Log.ERROR, e)
                            }
                        }

                        MediaHelper.MediaQueueItemsPath -> {
                            try {
                                updatePager(item)
                                val dataMap = DataMapItem.fromDataItem(item).dataMap
                                updateQueueItems(dataMap)
                                isPagerUpdated = true
                            } catch (e: Exception) {
                                Logger.writeLine(Log.ERROR, e)
                            }
                        }

                        MediaHelper.MediaPlayerStatePath -> {
                            deleteJob?.cancel()
                            val dataMap = DataMapItem.fromDataItem(item).dataMap
                            updatePlayerState(dataMap)
                        }
                    }
                } else if (event.type == DataEvent.TYPE_DELETED) {
                    val item = event.dataItem
                    when (item.uri.path) {
                        MediaHelper.MediaBrowserItemsPath -> {
                            mediaPagerState = mediaPagerState.copy(
                                supportsBrowser = false
                            )
                            isPagerUpdated = true
                        }

                        MediaHelper.MediaActionsPath -> {
                            mediaPagerState = mediaPagerState.copy(
                                supportsCustomActions = false
                            )
                            isPagerUpdated = true
                        }

                        MediaHelper.MediaQueueItemsPath -> {
                            mediaPagerState = mediaPagerState.copy(
                                supportsQueue = false,
                            )

                            viewModelState.update {
                                it.copy(
                                    activeQueueItemId = -1
                                )
                            }

                            isPagerUpdated = true
                        }

                        MediaHelper.MediaPlayerStatePath -> {
                            deleteJob?.cancel()
                            deleteJob = viewModelScope.launch delete@{
                                delay(1000)

                                if (!isActive) return@delete

                                updatePlayerState(DataMap())
                            }
                        }
                    }
                }
            }

            if (isPagerUpdated) {
                updatePagerJob = viewModelScope.launch updatePagerJob@{
                    delay(1000)

                    if (!isActive) return@updatePagerJob

                    viewModelState.update {
                        it.copy(
                            pagerState = mediaPagerState
                        )
                    }
                }
            }
        }
    }

    fun autoLaunch() {
        viewModelState.update {
            it.copy(
                mediaPlayerDetails = AppItemViewModel(),
                isAutoLaunch = false
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

    private fun updatePager() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            "/media"
                        ),
                        DataClient.FILTER_PREFIX
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    updatePager(item)
                }

                buff.release()

                viewModelState.update {
                    it.copy(pagerState = mediaPagerState)
                }
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private fun updatePager(item: DataItem) {
        when (item.uri.path) {
            MediaHelper.MediaBrowserItemsPath -> {
                mediaPagerState = mediaPagerState.copy(
                    supportsBrowser = try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                        !items.isNullOrEmpty()
                    } catch (e: Exception) {
                        Logger.writeLine(Log.ERROR, e)
                        false
                    }
                )
            }

            MediaHelper.MediaActionsPath -> {
                mediaPagerState = mediaPagerState.copy(
                    supportsCustomActions = try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                        !items.isNullOrEmpty()
                    } catch (e: Exception) {
                        Logger.writeLine(Log.ERROR, e)
                        false
                    }
                )
            }

            MediaHelper.MediaQueueItemsPath -> {
                mediaPagerState = mediaPagerState.copy(
                    supportsQueue = try {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS)
                        !items.isNullOrEmpty()
                    } catch (e: Exception) {
                        Logger.writeLine(Log.ERROR, e)
                        false
                    }
                )
            }
        }
    }

    private fun requestPlayerConnect() {
        viewModelScope.launch {
            if (connect()) {
                val state = uiState.value

                sendMessage(
                    mPhoneNodeWithApp!!.id,
                    MediaHelper.MediaPlayerConnectPath,
                    if (state.isAutoLaunch) state.isAutoLaunch.booleanToBytes() else state.mediaPlayerDetails.packageName?.stringToBytes()
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

    fun refreshStatus() {
        viewModelScope.launch {
            updateConnectionStatus()
            requestPlayerConnect()
            updatePager()
        }
    }

    fun refreshPlayerState() {
        viewModelScope.launch {
            // Request connect to media player
            requestVolumeStatus()
            updatePlayerState()
        }
    }

    private fun updatePlayerState(dataMap: DataMap) {
        viewModelScope.launch {
            val stateName = dataMap.getString(MediaHelper.KEY_MEDIA_PLAYBACKSTATE)
            val playbackState = stateName?.let { PlaybackState.valueOf(it) } ?: PlaybackState.NONE
            val title = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_TITLE)
            val artist = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_ARTIST)
            val artBitmap = dataMap.getAsset(MediaHelper.KEY_MEDIA_METADATA_ART)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(appContext),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val positionState = dataMap.getString(MediaHelper.KEY_MEDIA_POSITIONSTATE)?.let {
                JSONParser.deserializer(it, PositionState::class.java)
            }

            if (playbackState != PlaybackState.NONE) {
                viewModelState.update {
                    it.copy(
                        playerState = PlayerState(
                            playbackState = playbackState,
                            title = title,
                            artist = artist,
                            artworkBitmap = artBitmap,
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
    }

    private fun updatePlayerState() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaPlayerStatePath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaPlayerStatePath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updatePlayerState(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
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

    private fun requestVolumeUp() {
        requestMediaAction(MediaHelper.MediaVolumeUpPath)
    }

    private fun requestVolumeDown() {
        requestMediaAction(MediaHelper.MediaVolumeDownPath)
    }

    private fun requestVolumeStatus() {
        requestMediaAction(MediaHelper.MediaVolumeStatusPath)
    }

    private fun requestSetVolume(value: Int) {
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

    fun updateCustomControls() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaActionsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaActionsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateCustomControls(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateCustomControls(dataMap: DataMap) {
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getString(MediaHelper.KEY_MEDIA_ACTIONITEM_ACTION) ?: continue
            val icon = item.getAsset(MediaHelper.KEY_MEDIA_ACTIONITEM_ICON)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(appContext),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val title = item.getString(MediaHelper.KEY_MEDIA_ACTIONITEM_TITLE)

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaCustomItems = mediaItems
            )
        }
    }

    // Media Browser
    fun updateBrowserItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaBrowserItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaBrowserItemsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateBrowserItems(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateBrowserItems(dataMap: DataMap) {
        val isRoot = dataMap.getBoolean(MediaHelper.KEY_MEDIAITEM_ISROOT)
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(if (isRoot) items.size else items.size + 1)
        if (!isRoot) {
            mediaItems.add(MediaItemModel(MediaHelper.ACTIONITEM_BACK))
        }

        for (item in items) {
            val id = item.getString(MediaHelper.KEY_MEDIAITEM_ID) ?: continue
            val icon = item.getAsset(MediaHelper.KEY_MEDIAITEM_ICON)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(appContext),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val title = item.getString(MediaHelper.KEY_MEDIAITEM_TITLE)

            mediaItems.add(MediaItemModel(id).apply {
                this.icon = icon
                this.title = title
            })
        }

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaBrowserItems = mediaItems
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
    fun updateQueueItems() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val buff = Wearable.getDataClient(appContext)
                    .getDataItems(
                        WearableHelper.getWearDataUri(
                            "*",
                            MediaHelper.MediaQueueItemsPath
                        )
                    )
                    .await()

                for (i in 0 until buff.count) {
                    val item = buff[i]
                    if (MediaHelper.MediaQueueItemsPath == item.uri.path) {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        updateQueueItems(dataMap)
                    }
                }

                buff.release()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }
    }

    private suspend fun updateQueueItems(dataMap: DataMap) {
        val items = dataMap.getDataMapArrayList(MediaHelper.KEY_MEDIAITEMS) ?: emptyList()
        val mediaItems = ArrayList<MediaItemModel>(items.size)

        for (item in items) {
            val id = item.getLong(MediaHelper.KEY_MEDIAITEM_ID)
            val icon = item.getAsset(MediaHelper.KEY_MEDIAITEM_ICON)?.let {
                try {
                    ImageUtils.bitmapFromAssetStream(
                        Wearable.getDataClient(appContext),
                        it
                    )
                } catch (e: Exception) {
                    null
                }
            }
            val title = item.getString(MediaHelper.KEY_MEDIAITEM_TITLE)

            mediaItems.add(MediaItemModel(id.toString()).apply {
                this.icon = icon
                this.title = title
            })
        }

        val newQueueId = dataMap.getLong(MediaHelper.KEY_MEDIA_ACTIVEQUEUEITEM_ID, -1)

        viewModelState.update {
            it.copy(
                isLoading = false,
                mediaQueueItems = mediaItems,
                activeQueueItemId = newQueueId
            )
        }
    }

    fun requestQueueActionItem(itemId: String) {
        requestMediaAction(MediaHelper.MediaQueueItemsClickPath, itemId.stringToBytes())
    }
}