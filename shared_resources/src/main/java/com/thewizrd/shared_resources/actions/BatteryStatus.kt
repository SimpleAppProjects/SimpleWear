package com.thewizrd.shared_resources.actions

class BatteryStatus(var batteryLevel: Int, var isCharging: Boolean) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is BatteryStatus) return false

        if (batteryLevel != other.batteryLevel) return false
        if (isCharging != other.isCharging) return false

        return true
    }

    override fun hashCode(): Int {
        var result = batteryLevel
        result = 31 * result + isCharging.hashCode()
        return result
    }
}