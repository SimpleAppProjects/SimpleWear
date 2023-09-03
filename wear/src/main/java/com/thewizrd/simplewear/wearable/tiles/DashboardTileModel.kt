package com.thewizrd.simplewear.wearable.tiles

import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class DashboardTileModel {
    private var mConnectionStatus = WearConnectionStatus.DISCONNECTED

    private var battStatus: BatteryStatus? = null
    private val tileActions = mutableListOf<Actions>()
    private val actionMap = mutableMapOf<Actions, Action>().apply {
        // Add NormalActions
        putIfAbsent(Actions.LOCKSCREEN, NormalAction(Actions.LOCKSCREEN))
    }

    private val _tileState =
        MutableStateFlow(DashboardTileState(mConnectionStatus, battStatus, getActionMapping()))

    val tileState: StateFlow<DashboardTileState>
        get() = _tileState.asStateFlow()

    fun setConnectionStatus(status: WearConnectionStatus) {
        mConnectionStatus = status
        _tileState.update {
            it.copy(connectionStatus = status)
        }
    }

    fun updateBatteryStatus(status: BatteryStatus?) {
        battStatus = status
        _tileState.update {
            it.copy(batteryStatus = status)
        }
    }

    fun getAction(actionType: Actions): Action? = actionMap[actionType]
    fun setAction(actionType: Actions, action: Action) {
        actionMap[actionType] = action
        _tileState.update {
            it.copy(actions = getActionMapping())
        }
    }

    fun updateTileActions(actions: Collection<Actions>) {
        tileActions.clear()
        tileActions.addAll(actions)

        _tileState.update {
            it.copy(actions = getActionMapping())
        }
    }

    val actionCount: Int
        get() = tileActions.size

    private fun getActionMapping() = tileActions.associateWith { actionMap[it] }
}

data class DashboardTileState(
    val connectionStatus: WearConnectionStatus,
    val batteryStatus: BatteryStatus? = null,
    val actions: Map<Actions, Action?> = emptyMap(),
) {
    fun getAction(actionType: Actions): Action? = actions[actionType]

    val isEmpty = batteryStatus == null || actions.isEmpty()
}