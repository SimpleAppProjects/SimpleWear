package com.thewizrd.simplewear.wearable.tiles

import android.content.Context
import android.content.Intent
import androidx.lifecycle.lifecycleScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.EventBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.SuspendingTileService
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.Companion.tileModel
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileMessenger.PlayerAction
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.MediaPlayerTileRenderer.Companion.ID_PHONEDISCONNECTED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalHorologistApi::class)
class MediaPlayerTileProviderService : SuspendingTileService() {
    companion object {
        private const val TAG = "MediaPlayerTileProviderService"

        fun requestTileUpdate(context: Context) {
            getUpdater(context).requestUpdate(MediaPlayerTileProviderService::class.java)
        }
    }

    private val tileMessenger = MediaPlayerTileMessenger(this)
    private lateinit var tileStateFlow: StateFlow<MediaPlayerTileState?>

    override fun onCreate() {
        super.onCreate()
        Timber.tag(TAG).d("creating service...")

        tileMessenger.register()
        tileStateFlow = tileModel.tileState
            .stateIn(
                lifecycleScope,
                started = SharingStarted.WhileSubscribed(5000),
                null
            )
    }

    override fun onDestroy() {
        Timber.tag(TAG).d("destroying service...")
        tileMessenger.unregister()
        super.onDestroy()
    }

    override fun onTileEnterEvent(requestParams: EventBuilders.TileEnterEvent) {
        super.onTileEnterEvent(requestParams)

        Timber.tag(TAG).d("$TAG: onTileEnterEvent called with: tileId = ${requestParams.tileId}")

        requestTileUpdate(this)

        lifecycleScope.launch {
            tileMessenger.checkConnectionStatus()
            tileMessenger.requestPlayerConnect()
            tileMessenger.requestVolumeStatus()
            tileMessenger.updatePlayerStateAsync()
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)
        Timber.tag(TAG).d("$TAG: onTileLeaveEvent called with: tileId = ${requestParams.tileId}")

        lifecycleScope.launch {
            tileMessenger.requestPlayerDisconnect()
        }
    }

    private val tileRenderer = MediaPlayerTileRenderer(this)

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Timber.tag(TAG).d("tileRequest: ${requestParams.currentState}")

        tileMessenger.checkConnectionStatus()

        if (requestParams.currentState.lastClickableId.isNotEmpty()) {
            if (ID_OPENONPHONE == requestParams.currentState.lastClickableId || ID_PHONEDISCONNECTED == requestParams.currentState.lastClickableId) {
                runCatching {
                    startActivity(Intent(applicationContext, PhoneSyncActivity::class.java))
                }
            } else {
                // Process action
                runCatching {
                    Timber.tag(TAG)
                        .d("lastClickableId = ${requestParams.currentState.lastClickableId}")
                    val action = PlayerAction.valueOf(requestParams.currentState.lastClickableId)
                    val ret = tileMessenger.requestPlayerActionAsync(action)
                    Timber.tag(TAG).d("requestPlayerActionAsync = $ret")
                }
            }
        } else {
            tileMessenger.updatePlayerStateAsync()
        }

        val tileState = latestTileState()
        Timber.tag(TAG).d("State: ${tileState.title} - ${tileState.artist}")
        return tileRenderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): MediaPlayerTileState {
        return tileStateFlow.filterNotNull().first()
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        val tileState = latestTileState()
        return tileRenderer.produceRequestedResources(tileState, requestParams)
    }
}