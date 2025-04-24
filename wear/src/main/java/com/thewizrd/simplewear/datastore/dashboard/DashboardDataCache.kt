package com.thewizrd.simplewear.datastore.dashboard

import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus

data class DashboardDataCache(
    val batteryStatus: BatteryStatus? = null,
    val actions: Map<Actions, Action?> = emptyMap(),
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DashboardDataCache) return false

        if (batteryStatus != other.batteryStatus) return false
        if (actions != other.actions) return false

        return true
    }

    override fun hashCode(): Int {
        var result = batteryStatus?.hashCode() ?: 0
        result = 31 * result + actions.hashCode()
        return result
    }
}
