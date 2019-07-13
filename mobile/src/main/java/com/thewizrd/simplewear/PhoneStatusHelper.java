package com.thewizrd.simplewear;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.thewizrd.shared_resources.BatteryStatus;

public class PhoneStatusHelper {

    public static BatteryStatus getBatteryLevel(@NonNull Context context) {
        BatteryManager bm = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
        int batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        boolean isCharging = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int batStatus = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS);
            isCharging = (batStatus == BatteryManager.BATTERY_STATUS_CHARGING) || (batStatus == BatteryManager.BATTERY_STATUS_FULL);
        } else {
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);

            int batStatus = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            isCharging = (batStatus == BatteryManager.BATTERY_STATUS_CHARGING) || (batStatus == BatteryManager.BATTERY_STATUS_FULL);
        }
        return new BatteryStatus(batLevel, isCharging);
    }

    public static int getWifiState(@NonNull Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            WifiManager wifiMan = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wifiMan.getWifiState();
        }

        return WifiManager.WIFI_STATE_UNKNOWN;
    }

    public static boolean isWifiEnabled(@NonNull Context context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            WifiManager wifiMan = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wifiMan.isWifiEnabled();
        }

        return false;
    }

    public static boolean setWifiEnabled(@NonNull Context context, boolean enable) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            WifiManager wifiMan = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            return wifiMan.setWifiEnabled(enable);
        }

        return false;
    }

    public static boolean isBluetoothEnabled(@NonNull Context context) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.isEnabled();
        }

        return false;
    }

    public static boolean setBluetoothEnabled(@NonNull Context context, boolean enable) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            return enable ? mBluetoothAdapter.enable() : mBluetoothAdapter.disable();
        }

        return false;
    }

    public static boolean isMobileDataEnabled(@NonNull Context context) {
        boolean enabled = false;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());
            enabled = cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR);
        } else {
            enabled = Settings.Global.getInt(context.getContentResolver(), "mobile_data", 0) == 1;
        }

        return enabled;
    }
}
