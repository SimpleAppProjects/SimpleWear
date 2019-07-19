package com.thewizrd.shared_resources.helpers;

public class BatteryStatus {
    public int batteryLevel;
    public boolean isCharging;

    public BatteryStatus(int batteryLevel, boolean isCharging) {
        this.batteryLevel = batteryLevel;
        this.isCharging = isCharging;
    }
}
