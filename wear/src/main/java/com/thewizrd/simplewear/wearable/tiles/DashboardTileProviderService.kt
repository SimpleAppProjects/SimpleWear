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
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.datastore.dashboard.dashboardDataStore
import com.thewizrd.simplewear.preferences.DashboardTileUtils.DEFAULT_TILES
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_PHONEDISCONNECTED
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

class DashboardTileProviderService : SuspendingTileService() {
    companion object {
        private const val TAG = "DashTileProviderService"

        fun requestTileUpdate(context: Context) {
            updateJob?.cancel()

            updateJob = appLib.appScope.launch {
                delay(1000)
                if (isActive) {
                    Logger.debug(TAG, "requesting tile update")
                    getUpdater(context).requestUpdate(DashboardTileProviderService::class.java)
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

    private lateinit var tileMessenger: DashboardTileMessenger
    private lateinit var tileStateFlow: StateFlow<DashboardTileState?>
    private lateinit var tileRenderer: DashboardTileRenderer

    override fun onCreate() {
        super.onCreate()
        Logger.debug(TAG, "creating service...")

        tileMessenger = DashboardTileMessenger(this)
        tileRenderer = DashboardTileRenderer(this)

        tileMessenger.register()
        tileStateFlow = this.dashboardDataStore.data
            .combine(tileMessenger.connectionState) { cache, connectionStatus ->
                val userActions = Settings.getDashboardTileConfig() ?: DEFAULT_TILES

                DashboardTileState(
                    connectionStatus = connectionStatus,
                    batteryStatus = cache.batteryStatus,
                    actions = userActions.associateWith {
                        cache.actions.run {
                            // Add NormalActions
                            this.plus(Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN))
                        }[it]
                    },
                    showBatteryStatus = Settings.isShowTileBatStatus()
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

        lifecycleScope.launch {
            tileMessenger.checkConnectionStatus()
            tileMessenger.requestUpdate()
        }.invokeOnCompletion {
            if (it is CancellationException || !isUpdating) {
                // If update timed out
                requestTileUpdate(this)
            }
        }
    }

    private fun onTileInteractionLeaveEvent(requestParams: TileInteractionEvent) {
        Logger.debug(TAG, "$TAG: onTileLeaveEvent called with: tileId = ${requestParams.tileId}")
        isInFocus = false
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
                    val action = Actions.valueOf(requestParams.currentState.lastClickableId)

                    val state = latestTileState()
                    val actionState = state.getAction(action)

                    withTimeoutOrNull(5000) {
                        AnalyticsLogger.logEvent("dashtile_action_clicked", Bundle().apply {
                            putString("action", action.name)
                        })

                        val ret = tileMessenger.processActionAsync(state, action)
                        Logger.debug(TAG, "requestPlayerActionAsync = $ret")
                    }

                    if (Action.getDefaultAction(action) !is NormalAction) {
                        // Try to await for action change
                        withTimeoutOrNull(5000) {
                            supervisorScope {
                                tileStateFlow.collectLatest { newState ->
                                    if (newState?.getAction(action) != actionState) {
                                        coroutineContext.cancel()
                                    }
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
            AnalyticsLogger.logEvent("dashtile_state_empty", Bundle().apply {
                putBoolean("isCoroutineActive", coroutineContext.isActive)
            })
        }

        val endTime = SystemClock.elapsedRealtimeNanos()
        Logger.debug(TAG, "Duration - ${Duration.ofNanos(endTime - startTime)}")
        Logger.debug(TAG, "Rendering timeline...")
        return tileRenderer.renderTimeline(tileState, requestParams)
    }

    private suspend fun latestTileState(): DashboardTileState {
        var tileState = tileStateFlow.filterNotNull().first()

        if (tileState.isEmpty) {
            Logger.debug(TAG, "No tile state available. loading from remote...")
            tileMessenger.requestUpdate()

            // Try to await for full metadata change
            runCatching {
                withTimeoutOrNull(5000) {
                    supervisorScope {
                        tileStateFlow.filterNotNull().collectLatest { newState ->
                            if (newState.actions.isNotEmpty() && newState.batteryStatus != null) {
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
        return tileRenderer.produceRequestedResources(Unit, requestParams)
    }
}