@file:OptIn(ExperimentalHorologistApi::class)

package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders.TileInteractionEvent
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.google.common.util.concurrent.ListenableFuture
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.datastore.media.appInfoDataStore
import com.thewizrd.simplewear.datastore.media.artworkDataStore
import com.thewizrd.simplewear.datastore.media.mediaDataStore
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.PlayerAction
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.NowPlayingTileRenderer.Companion.ID_PHONEDISCONNECTED
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext

class NowPlayingTileProviderService : SuspendingTileService() {
    companion object {
        private const val TAG = "NowPlayingTileProviderService"

        fun requestTileUpdate(context: Context) {
            updateJob?.cancel()

            // Defer update to prevent spam
            updateJob = appLib.appScope.launch {
                delay(1000)
                if (isActive) {
                    Logger.debug(TAG, "requesting tile update")
                    getUpdater(context).requestUpdate(NowPlayingTileProviderService::class.java)
                }
            }
        }

        @JvmStatic
        @Volatile
        var isInFocus: Boolean = false
            private set

        @JvmStatic
        @Volatile
        var isUpdating: Boolean = false
            private set

        private var updateJob: Job? = null
    }

    private lateinit var tileMessenger: MediaPlayerTileMessenger
    private lateinit var tileStateFlow: StateFlow<MediaPlayerTileState?>
    private lateinit var tileRenderer: NowPlayingTileRenderer

    override fun onCreate() {
        super.onCreate()
        Logger.debug(TAG, "creating service...")

        tileMessenger = MediaPlayerTileMessenger(this)
        tileRenderer = NowPlayingTileRenderer(this)

        tileMessenger.register()
        tileStateFlow = combine(
            this.mediaDataStore.data,
            this.artworkDataStore.data,
            this.appInfoDataStore.data,
            tileMessenger.connectionState
        ) { mediaCache, artwork, appInfo, connectionStatus ->
            MediaPlayerTileState(
                connectionStatus = connectionStatus,
                title = mediaCache.mediaPlayerState?.mediaMetaData?.title,
                artist = mediaCache.mediaPlayerState?.mediaMetaData?.artist,
                artwork = artwork,
                playbackState = mediaCache.mediaPlayerState?.playbackState,
                positionState = mediaCache.mediaPlayerState?.mediaMetaData?.positionState,
                audioStreamState = mediaCache.audioStreamState,
                appIcon = appInfo.iconBitmap
            )
        }
            .stateIn(
                lifecycleScope,
                started = SharingStarted.WhileSubscribed(2000),
                initialValue = null
            )
    }

    override fun onDestroy() {
        isUpdating = false
        Logger.debug(TAG, "destroying service...")
        tileMessenger.unregister()
        super.onDestroy()
    }

    private fun onTileInteractionEnterEvent(requestParams: TileInteractionEvent) {
        Logger.debug(TAG, "onTileEnterEvent called with: tileId = ${requestParams.tileId}")
        AnalyticsLogger.logEvent("on_tile_enter", Bundle().apply {
            putString("tile", TAG)
        })
        isInFocus = true

        appLib.appScope.launch {
            tileMessenger.checkConnectionStatus()
            tileMessenger.requestPlayerConnect()
            tileMessenger.requestUpdatePlayerState()
        }.invokeOnCompletion {
            if (it is CancellationException || !isUpdating) {
                // If update timed out
                requestTileUpdate(this)
            }
        }
    }

    private fun onTileInteractionLeaveEvent(requestParams: TileInteractionEvent) {
        Logger.debug(TAG, "onTileLeaveEvent called with: tileId = ${requestParams.tileId}")
        isInFocus = false

        appLib.appScope.launch {
            tileMessenger.requestPlayerDisconnect()
        }
    }

    override fun onRecentInteractionEventsAsync(events: List<TileInteractionEvent>): ListenableFuture<Void?> {
        return SuspendToFutureAdapter.launchFuture {
            val lastEvent = events.lastOrNull()

            when (lastEvent?.eventType) {
                TileInteractionEvent.ENTER -> {
                    onTileInteractionEnterEvent(lastEvent)
                }

                TileInteractionEvent.LEAVE -> {
                    onTileInteractionLeaveEvent(lastEvent)
                }

                TileInteractionEvent.UNKNOWN -> { /* no-op */
                }
            }

            null
        }
    }

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Logger.debug(TAG, "tileRequest: ${requestParams.currentState}")
        val startTime = SystemClock.elapsedRealtimeNanos()
        isUpdating = true

        tileMessenger.checkConnectionStatus()

        if (requestParams.currentState.lastClickableId.isNotEmpty()) {
            if (ID_OPENONPHONE == requestParams.currentState.lastClickableId || ID_PHONEDISCONNECTED == requestParams.currentState.lastClickableId) {
                runCatching {
                    startActivity(Intent(applicationContext, PhoneSyncActivity::class.java))
                }
            } else {
                // Process action
                runCatching {
                    Logger.debug(
                        TAG,
                        "lastClickableId = ${requestParams.currentState.lastClickableId}"
                    )
                    val action = PlayerAction.valueOf(requestParams.currentState.lastClickableId)

                    val state = latestTileState()

                    withTimeoutOrNull(5000) {
                        val ret = tileMessenger.requestPlayerActionAsync(action)
                        Logger.debug(TAG, "requestPlayerActionAsync = $ret")
                    }

                    // Try to await for full metadata change
                    withTimeoutOrNull(5000) {
                        supervisorScope {
                            var songChanged = false
                            tileStateFlow.collectLatest { newState ->
                                if (!songChanged && newState?.title != state.title && newState?.artist != state.artist) {
                                    // new song; wait for artwork
                                    songChanged = true
                                } else if (songChanged && !newState?.artwork.contentEquals(state.artwork)) {
                                    coroutineContext.cancel()
                                } else if (newState?.playbackState != state.playbackState) {
                                    // only playstate change
                                    coroutineContext.cancel()
                                }
                            }
                        }
                    }
                }
            }
        }

        isUpdating = false
        val tileState = latestTileState()

        if (tileState.isEmpty) {
            AnalyticsLogger.logEvent("mediatile_state_empty", Bundle().apply {
                putBoolean("isCoroutineActive", coroutineContext.isActive)
            })
        }

        val endTime = SystemClock.elapsedRealtimeNanos()
        Logger.debug(TAG, "Current State - ${tileState.title}:${tileState.artist}")
        Logger.debug(TAG, "Duration - ${Duration.ofNanos(endTime - startTime)}")
        Logger.debug(TAG, "Rendering timeline...")
        return tileRenderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): MediaPlayerTileState {
        var tileState = tileStateFlow.filterNotNull().first()

        if (tileState.isEmpty) {
            Logger.debug(TAG, "No tile state available. loading from remote...")
            tileMessenger.updatePlayerStateFromRemote()

            // Try to await for full metadata change
            runCatching {
                withTimeoutOrNull(5000) {
                    supervisorScope {
                        var songChanged = false

                        tileStateFlow.filterNotNull().collectLatest { newState ->
                            if (!songChanged && newState.title != tileState.title && newState.artist != tileState.artist) {
                                // new song; wait for artwork
                                tileState = newState
                                songChanged = true
                            } else if (songChanged && !newState.artwork.contentEquals(tileState.artwork)) {
                                tileState = newState
                                coroutineContext.cancel()
                            }
                        }
                    }
                }
            }
        }

        return tileState
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        val tileState = latestTileState()
        return tileRenderer.produceRequestedResources(tileState, requestParams)
    }
}