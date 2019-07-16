package com.thewizrd.simplewear.helpers;

import android.Manifest;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.thewizrd.shared_resources.BatteryStatus;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ValueDirection;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.ScreenLockAdminReceiver;
import com.thewizrd.simplewear.services.TorchService;

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
        boolean mobileDataSettingEnabled = Settings.Global.getInt(context.getContentResolver(), "mobile_data", 0) == 1;

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());

        return cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || mobileDataSettingEnabled;
    }

    public static boolean isLocationEnabled(@NonNull Context context) {
        LocationManager locMan = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        boolean isGPSEnabled = false;
        boolean isNetEnabled = false;
        if (locMan != null) {
            isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        }

        return isGPSEnabled || isNetEnabled;
    }

    public static boolean isCameraPermissionEnabled(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(context.getApplicationContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean isTorchEnabled(@NonNull Context context) {
        return isServiceRunning(context, TorchService.class);
    }

    private static boolean isServiceRunning(@NonNull Context context, Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    public static boolean setTorchEnabled(@NonNull Context context, boolean enable) {
        boolean success = false;

        if (!isCameraPermissionEnabled(context))
            return false;

        try {
            TorchService.enqueueWork(context.getApplicationContext(), new Intent(context.getApplicationContext(), TorchService.class)
                    .setAction(enable ? TorchService.ACTION_START_LIGHT : TorchService.ACTION_END_LIGHT));
            success = true;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            success = false;
        }

        return success;
    }

    public static boolean isDeviceAdminEnabled(@NonNull Context context) {
        DevicePolicyManager mDPM = (DevicePolicyManager) context.getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mScreenLockAdmin = new ComponentName(context.getApplicationContext(), ScreenLockAdminReceiver.class);
        return mDPM.isAdminActive(mScreenLockAdmin);
    }

    public static boolean lockScreen(@NonNull Context context) {
        try {
            DevicePolicyManager mDPM = (DevicePolicyManager) context.getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
            mDPM.lockNow();
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            return false;
        }

        return true;
    }

    public static boolean setVolume(@NonNull Context context, ValueDirection direction) {
        try {
            AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            switch (direction) {
                case UP:
                    audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;
                case DOWN:
                    audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI);
                    break;
            }
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            return false;
        }

        return true;
    }

    public static boolean isNotificationAccessAllowed(@NonNull Context context) {
        NotificationManager notMan = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
        return notMan.isNotificationPolicyAccessGranted();
    }

    public static DNDChoice getDNDState(@NonNull Context context) {
        NotificationManager notMan = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

        switch (notMan.getCurrentInterruptionFilter()) {
            case NotificationManager.INTERRUPTION_FILTER_ALARMS:
                return DNDChoice.ALARMS;
            case NotificationManager.INTERRUPTION_FILTER_ALL:
                return DNDChoice.OFF;
            case NotificationManager.INTERRUPTION_FILTER_NONE:
                return DNDChoice.SILENCE;
            case NotificationManager.INTERRUPTION_FILTER_PRIORITY:
                return DNDChoice.PRIORITY;
            case NotificationManager.INTERRUPTION_FILTER_UNKNOWN:
            default:
                return DNDChoice.OFF;
        }
    }

    public static boolean setDNDState(@NonNull Context context, DNDChoice dnd) {
        if (!isNotificationAccessAllowed(context))
            return false;

        try {
            NotificationManager notMan = (NotificationManager) context.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);

            switch (dnd) {
                case OFF:
                    notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL);
                    break;
                case PRIORITY:
                    notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY);
                    break;
                case ALARMS:
                    notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS);
                    break;
                case SILENCE:
                    notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);
                    break;
                default:
                    return false;
            }
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            return false;
        }

        return true;
    }

    public static RingerChoice getRingerState(@NonNull Context context) {
        RingerChoice ringerChoice;

        AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        switch (audioMan.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                ringerChoice = RingerChoice.SILENT;
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                ringerChoice = RingerChoice.VIBRATION;
                break;
            case AudioManager.RINGER_MODE_NORMAL:
            default:
                ringerChoice = RingerChoice.SOUND;
                break;
        }

        return ringerChoice;
    }

    public static boolean setRingerState(@NonNull Context context, RingerChoice ringer) {
        try {
            AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

            switch (ringer) {
                case VIBRATION:
                default:
                    audioMan.setRingerMode(AudioManager.RINGER_MODE_VIBRATE);
                    break;
                case SOUND:
                    audioMan.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
                    break;
                case SILENT:
                    audioMan.setRingerMode(AudioManager.RINGER_MODE_SILENT);
                    break;
            }
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            return false;
        }

        return true;
    }
}
