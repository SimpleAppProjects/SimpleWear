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
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.preferences.DashboardTileUtils.DEFAULT_TILES
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.wearable.tiles.DashboardTileMessenger.Companion.tileModel
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_PHONEDISCONNECTED
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

@OptIn(ExperimentalHorologistApi::class)
class DashboardTileProviderService : SuspendingTileService() {
    companion object {
        private const val TAG = "DashTileProviderService"

        fun requestTileUpdate(context: Context) {
            getUpdater(context).requestUpdate(DashboardTileProviderService::class.java)
        }
    }

    private val tileMessenger = DashboardTileMessenger(this)
    private lateinit var tileStateFlow: StateFlow<DashboardTileState?>

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

        // Update tile actions
        tileModel.updateTileActions(Settings.getDashboardTileConfig() ?: DEFAULT_TILES)

        requestTileUpdate(this)

        lifecycleScope.launch {
            tileMessenger.checkConnectionStatus()
            tileMessenger.requestUpdate()
        }
    }

    override fun onTileLeaveEvent(requestParams: EventBuilders.TileLeaveEvent) {
        super.onTileLeaveEvent(requestParams)
        Timber.tag(TAG).d("$TAG: onTileLeaveEvent called with: tileId = ${requestParams.tileId}")
    }

    private val tileRenderer = DashboardTileRenderer(this)

    override suspend fun tileRequest(requestParams: RequestBuilders.TileRequest): TileBuilders.Tile {
        Timber.tag(TAG).d("tileRequest: ${requestParams.currentState}")

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
                    val action = Actions.valueOf(requestParams.currentState.lastClickableId)
                    withTimeoutOrNull(5000) {
                        tileMessenger.processActionAsync(action)
                    }
                }
            }
        }

        tileMessenger.checkConnectionStatus()

        if (tileModel.actionCount == 0) {
            tileModel.updateTileActions(Settings.getDashboardTileConfig() ?: DEFAULT_TILES)
        }

        tileModel.setShowBatteryStatus(Settings.isShowTileBatStatus())

        return tileRenderer.renderTimeline(tileModel.tileState.value, requestParams)
    }

    private suspend fun latestTileState(): DashboardTileState {
        return tileStateFlow.filterNotNull().first()
    }

    override suspend fun resourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ResourceBuilders.Resources {
        return tileRenderer.produceRequestedResources(Unit, requestParams)
    }
}