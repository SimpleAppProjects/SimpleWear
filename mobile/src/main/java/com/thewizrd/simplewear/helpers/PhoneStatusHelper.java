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
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.thewizrd.shared_resources.helpers.ActionStatus;
import com.thewizrd.shared_resources.helpers.BatteryStatus;
import com.thewizrd.shared_resources.helpers.DNDChoice;
import com.thewizrd.shared_resources.helpers.RingerChoice;
import com.thewizrd.shared_resources.helpers.ValueDirection;
import com.thewizrd.shared_resources.tasks.AsyncTask;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.ScreenLockAdminReceiver;
import com.thewizrd.simplewear.services.TorchService;

import java.util.concurrent.Callable;

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

    public static ActionStatus setWifiEnabled(@NonNull Context context, boolean enable) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            ActionStatus status;
            try {
                WifiManager wifiMan = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                status = wifiMan.setWifiEnabled(enable) ? ActionStatus.SUCCESS : ActionStatus.FAILURE;
            } catch (Exception e) {
                Logger.writeLine(Log.ERROR, e);
                status = ActionStatus.FAILURE;
            }

            return status;
        }

        return ActionStatus.PERMISSION_DENIED;
    }

    public static boolean isBluetoothEnabled(@NonNull Context context) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter != null) {
            return mBluetoothAdapter.isEnabled();
        }

        return false;
    }

    public static ActionStatus setBluetoothEnabled(@NonNull Context context, boolean enable) {
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter != null) {
            return (enable ? mBluetoothAdapter.enable() : mBluetoothAdapter.disable()) ?
                    ActionStatus.SUCCESS : ActionStatus.FAILURE;
        }

        return ActionStatus.FAILURE;
    }

    public static boolean isMobileDataEnabled(@NonNull Context context) {
        boolean mobileDataSettingEnabled = Settings.Global.getInt(context.getContentResolver(), "mobile_data", 0) == 1;

        ConnectivityManager cm =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkCapabilities cap = cm.getNetworkCapabilities(cm.getActiveNetwork());

        return (cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) || mobileDataSettingEnabled;
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

    public static ActionStatus setTorchEnabled(@NonNull Context context, boolean enable) {
        if (!isCameraPermissionEnabled(context))
            return ActionStatus.PERMISSION_DENIED;

        ActionStatus status;

        try {
            TorchService.enqueueWork(context.getApplicationContext(), new Intent(context.getApplicationContext(), TorchService.class)
                    .setAction(enable ? TorchService.ACTION_START_LIGHT : TorchService.ACTION_END_LIGHT));
            status = ActionStatus.SUCCESS;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            status = ActionStatus.FAILURE;
        }

        return status;
    }

    public static boolean isDeviceAdminEnabled(@NonNull Context context) {
        DevicePolicyManager mDPM = (DevicePolicyManager) context.getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName mScreenLockAdmin = new ComponentName(context.getApplicationContext(), ScreenLockAdminReceiver.class);
        return mDPM.isAdminActive(mScreenLockAdmin);
    }

    public static ActionStatus lockScreen(@NonNull Context context) {
        if (!isDeviceAdminEnabled(context))
            return ActionStatus.PERMISSION_DENIED;

        ActionStatus status;

        try {
            DevicePolicyManager mDPM = (DevicePolicyManager) context.getApplicationContext().getSystemService(Context.DEVICE_POLICY_SERVICE);
            mDPM.lockNow();
            status = ActionStatus.SUCCESS;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            status = ActionStatus.FAILURE;
        }

        return status;
    }

    public static ActionStatus setVolume(@NonNull Context context, ValueDirection direction) {
        ActionStatus status;

        try {
            AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
            PowerManager powerMan = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            boolean isInteractive = powerMan.isInteractive();
            int flags = AudioManager.FLAG_PLAY_SOUND;
            if (isInteractive) flags |= AudioManager.FLAG_SHOW_UI;

            switch (direction) {
                case UP:
                    audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, flags);
                    break;
                case DOWN:
                    audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, flags);
                    break;
            }

            status = ActionStatus.SUCCESS;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            status = ActionStatus.FAILURE;
        }

        return status;
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

    public static ActionStatus setDNDState(@NonNull Context context, DNDChoice dnd) {
        if (!isNotificationAccessAllowed(context))
            return ActionStatus.PERMISSION_DENIED;

        ActionStatus status;

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
                    return ActionStatus.FAILURE;
            }

            status = ActionStatus.SUCCESS;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            status = ActionStatus.FAILURE;
        }

        return status;
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

    public static ActionStatus setRingerState(@NonNull Context context, RingerChoice ringer) {
        ActionStatus status;

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
            status = ActionStatus.SUCCESS;
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
            status = ActionStatus.FAILURE;
        }

        return status;
    }

    public static ActionStatus sendPlayMusicCommand(@NonNull Context context) {
        // Add a minor delay before sending the command
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }

        final AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY);
        audioMan.dispatchMediaKeyEvent(event);

        // Wait for a second to see if music plays
        boolean musicActive = AsyncTask.await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(1000);
                return audioMan.isMusicActive();
            }
        });

        return musicActive ? ActionStatus.SUCCESS : ActionStatus.FAILURE;
    }

    public static ActionStatus sendPlayMusicCommand(@NonNull Context context, @NonNull Intent playIntent) {
        // Add a minor delay before sending the command
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
        }

        final AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        context.sendBroadcast(playIntent);

        // Wait for a second to see if music plays
        boolean musicActive = AsyncTask.await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(1000);
                return audioMan.isMusicActive();
            }
        });

        return musicActive ? ActionStatus.SUCCESS : ActionStatus.FAILURE;
    }

    public static ActionStatus isMusicActive(@NonNull Context context) {
        final AudioManager audioMan = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // Wait for a second to see if music plays
        boolean musicActive = AsyncTask.await(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                Thread.sleep(1000);
                return audioMan.isMusicActive();
            }
        });

        return musicActive ? ActionStatus.SUCCESS : ActionStatus.FAILURE;
    }
}
