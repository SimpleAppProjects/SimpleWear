package com.thewizrd.simplewear.wearable.tiles.unofficial

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.google.android.clockwork.tiles.TileData
import com.google.android.clockwork.tiles.TileProviderService
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.datastore.dashboard.dashboardDataStore
import com.thewizrd.simplewear.preferences.DashboardTileUtils.DEFAULT_TILES
import com.thewizrd.simplewear.preferences.DashboardTileUtils.MAX_BUTTONS
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.wearable.tiles.DashboardTileMessenger
import com.thewizrd.simplewear.wearable.tiles.DashboardTileState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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
import java.util.Locale

class DashboardTileProviderService : TileProviderService() {
    companion object {
        private const val TAG = "DashTileProviderService"
    }

    private var mInFocus = false
    private var id = -1

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var tileMessenger: DashboardTileMessenger
    private lateinit var tileStateFlow: StateFlow<DashboardTileState?>

    override fun onCreate() {
        super.onCreate()
        Logger.debug(TAG, "creating service...")

        tileMessenger = DashboardTileMessenger(this, isLegacyTile = true)
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
                scope,
                started = SharingStarted.WhileSubscribed(2000),
                initialValue = null
            )

        scope.launch {
            tileStateFlow.collectLatest {
                if (mInFocus && isActive && !isIdForDummyData(id)) {
                    sendRemoteViews()
                }
            }
        }
    }

    override fun onDestroy() {
        Logger.debug(TAG, "destroying service...")
        tileMessenger.unregister()
        super.onDestroy()
        scope.cancel()
    }

    override fun onTileUpdate(tileId: Int) {
        Logger.debug(TAG, "onTileUpdate called with: tileId = $tileId")

        if (!isIdForDummyData(tileId)) {
            id = tileId

            scope.launch {
                sendRemoteViews()
            }
        }
    }

    override fun onTileFocus(tileId: Int) {
        super.onTileFocus(tileId)
        Logger.debug(TAG, "onTileFocus called with: tileId = $tileId")

        if (!isIdForDummyData(tileId)) {
            id = tileId
            mInFocus = true
            AnalyticsLogger.logEvent("on_tile_enter", Bundle().apply {
                putString("tile", TAG)
                putBoolean("isUnofficial", true)
            })

            scope.launch {
                tileMessenger.checkConnectionStatus()
                tileMessenger.requestUpdate()

                sendRemoteViews()
            }
        }
    }

    override fun onTileBlur(tileId: Int) {
        super.onTileBlur(tileId)

        Logger.debug(TAG, "$TAG: onTileBlur called with: tileId = $tileId")
        if (!isIdForDummyData(tileId)) {
            mInFocus = false
        }
    }

    private suspend fun sendRemoteViews() {
        Logger.debug(TAG, "$TAG: sendRemoteViews")

        val tileState = latestTileState()
        val updateViews = buildUpdate(tileState)

        val tileData = TileData.Builder()
            .setRemoteViews(updateViews)
            .build()

        sendUpdate(id, tileData)
    }

    private fun buildUpdate(tileState: DashboardTileState): RemoteViews {
        val views: RemoteViews

        if (tileState.connectionStatus != WearConnectionStatus.CONNECTED) {
            views = RemoteViews(applicationContext.packageName, R.layout.tile_disconnected)
            when (tileState.connectionStatus) {
                WearConnectionStatus.APPNOTINSTALLED -> {
                    views.setTextViewText(R.id.message, getString(R.string.error_notinstalled))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.common_full_open_on_phone
                    )
                }

                else -> {
                    views.setTextViewText(R.id.message, getString(R.string.status_disconnected))
                    views.setImageViewResource(
                        R.id.imageButton,
                        R.drawable.ic_phonelink_erase_white_24dp
                    )
                }
            }
            views.setOnClickPendingIntent(R.id.tile, getTapIntent(applicationContext))
            return views
        }

        views = RemoteViews(applicationContext!!.packageName, R.layout.tile_layout_dashboard)
        views.setOnClickPendingIntent(R.id.tile, getTapIntent(applicationContext))

        if (tileState.batteryStatus != null) {
            val battValue = String.format(
                Locale.ROOT, "%d%%, %s", tileState.batteryStatus.batteryLevel,
                if (tileState.batteryStatus.isCharging) applicationContext.getString(R.string.batt_state_charging) else applicationContext.getString(
                    R.string.batt_state_discharging
                )
            )
            views.setTextViewText(R.id.batt_stat_text, battValue)
        }

        if (Settings.isShowTileBatStatus()) {
            views.setViewVisibility(R.id.batt_stat_layout, View.VISIBLE)
            views.setViewVisibility(R.id.spacer, View.GONE)
        } else {
            views.setViewVisibility(R.id.batt_stat_layout, View.GONE)
            views.setViewVisibility(R.id.spacer, View.VISIBLE)
        }

        val actions = tileState.actions.keys.toList()

        for (i in 0 until MAX_BUTTONS) {
            val action = actions.getOrNull(i)
            updateButton(views, i + 1, tileState, action)
        }

        return views
    }

    private fun updateButton(
        views: RemoteViews,
        buttonIndex: Int,
        tileState: DashboardTileState,
        action: Actions?
    ) {
        val layoutId = when (buttonIndex) {
            1 -> R.id.button_1_layout
            2 -> R.id.button_2_layout
            3 -> R.id.button_3_layout
            4 -> R.id.button_4_layout
            5 -> R.id.button_5_layout
            6 -> R.id.button_6_layout
            else -> return
        }

        val buttonId = when (buttonIndex) {
            1 -> R.id.button_1
            2 -> R.id.button_2
            3 -> R.id.button_3
            4 -> R.id.button_4
            5 -> R.id.button_5
            6 -> R.id.button_6
            else -> return
        }

        if (action != null) {
            tileState.actions[action]?.let {
                val model = ActionButtonViewModel(it)
                views.setImageViewResource(buttonId, model.drawableResId)
                views.setInt(
                    buttonId,
                    "setBackgroundResource",
                    if (model.buttonState != false) R.drawable.round_button_enabled else R.drawable.round_button_disabled
                )
                views.setOnClickPendingIntent(
                    buttonId,
                    getActionClickIntent(applicationContext, it.actionType)
                )
                views.setContentDescription(
                    buttonId,
                    model.actionLabelResId.takeIf { it != 0 }?.let {
                        applicationContext.getString(it)
                    }
                )
            }
            views.setViewVisibility(layoutId, View.VISIBLE)
        } else {
            views.setViewVisibility(layoutId, View.GONE)
        }
    }

    private fun getTapIntent(context: Context): PendingIntent {
        val onClickIntent = Intent(context.applicationContext, PhoneSyncActivity::class.java)
        return PendingIntent.getActivity(context, 0, onClickIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getActionClickIntent(context: Context, action: Actions): PendingIntent {
        val onClickIntent =
            Intent(context.applicationContext, DashboardTileProviderService::class.java)
                .setAction(action.name)
        return PendingIntent.getService(
            context,
            action.value,
            onClickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let {
            val action = Actions.valueOf(it)

            scope.launch {
                val state = latestTileState()
                tileMessenger.processActionAsync(state, action)
            }
        }

        return super.onStartCommand(intent, flags, startId)
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
}