package com.thewizrd.simplewear.helpers

import android.Manifest
import android.app.ActivityManager
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.ScreenLockAdminReceiver
import com.thewizrd.simplewear.services.TorchService
import com.thewizrd.simplewear.services.TorchService.Companion.enqueueWork
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

object PhoneStatusHelper {
    fun getBatteryLevel(context: Context): BatteryStatus {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batLevel = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val isCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val batStatus = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            batStatus == BatteryManager.BATTERY_STATUS_CHARGING || batStatus == BatteryManager.BATTERY_STATUS_FULL
        } else {
            val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val batteryStatus = context.registerReceiver(null, ifilter)
            val batStatus = batteryStatus!!.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            batStatus == BatteryManager.BATTERY_STATUS_CHARGING || batStatus == BatteryManager.BATTERY_STATUS_FULL
        }
        return BatteryStatus(batLevel, isCharging)
    }

    fun getWifiState(context: Context): Int {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            val wifiMan = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return wifiMan.wifiState
        }
        return WifiManager.WIFI_STATE_UNKNOWN
    }

    fun isWifiEnabled(context: Context): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            val wifiMan = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            return wifiMan.isWifiEnabled
        }
        return false
    }

    fun setWifiEnabled(context: Context, enable: Boolean): ActionStatus {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_WIFI_STATE) == PackageManager.PERMISSION_GRANTED) {
            val status: ActionStatus
            status = try {
                val wifiMan = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (wifiMan.setWifiEnabled(enable)) ActionStatus.SUCCESS else ActionStatus.FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
            return status
        }
        return ActionStatus.PERMISSION_DENIED
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return mBluetoothAdapter?.isEnabled ?: false
    }

    fun setBluetoothEnabled(context: Context, enable: Boolean): ActionStatus {
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return if (mBluetoothAdapter != null) {
            if (if (enable) mBluetoothAdapter.enable() else mBluetoothAdapter.disable()) ActionStatus.SUCCESS else ActionStatus.FAILURE
        } else ActionStatus.FAILURE
    }

    fun isMobileDataEnabled(context: Context): Boolean {
        val mobileDataSettingEnabled = Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val cap = cm.getNetworkCapabilities(cm.activeNetwork)
        return cap != null && cap.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || mobileDataSettingEnabled
    }

    fun isLocationEnabled(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            LocationManagerCompat.isLocationEnabled(context.getSystemService(LocationManager::class.java)!!)
        } else {
            val locMan = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            isGPSEnabled || isNetEnabled
        }
    }

    fun getLocationState(context: Context): LocationState {
        val locMan = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGPSEnabled = locMan.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val isNetEnabled = locMan.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        return if (isGPSEnabled && isNetEnabled) {
            LocationState.HIGH_ACCURACY
        } else if (isGPSEnabled) {
            LocationState.SENSORS_ONLY
        } else if (isNetEnabled) {
            LocationState.BATTERY_SAVING
        } else {
            LocationState.OFF
        }
    }

    fun isCameraPermissionEnabled(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context.applicationContext, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    fun isTorchEnabled(context: Context): Boolean {
        return isServiceRunning(context, TorchService::class.java)
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }

    fun setTorchEnabled(context: Context, enable: Boolean): ActionStatus {
        if (!isCameraPermissionEnabled(context)) return ActionStatus.PERMISSION_DENIED
        return try {
            enqueueWork(context.applicationContext, Intent(context.applicationContext, TorchService::class.java)
                    .setAction(if (enable) TorchService.ACTION_START_LIGHT else TorchService.ACTION_END_LIGHT))
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun isDeviceAdminEnabled(context: Context): Boolean {
        val mDPM = context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val mScreenLockAdmin = ComponentName(context.applicationContext, ScreenLockAdminReceiver::class.java)
        return mDPM.isAdminActive(mScreenLockAdmin)
    }

    fun lockScreen(context: Context): ActionStatus {
        if (!isDeviceAdminEnabled(context)) return ActionStatus.PERMISSION_DENIED
        return try {
            val mDPM = context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            mDPM.lockNow()
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun getStreamVolume(context: Context, streamType: AudioStreamType): AudioStreamState? {
        return try {
            val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (streamType) {
                AudioStreamType.MUSIC -> {
                    val currVol = audioMan.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioMan.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
                    val maxVol = audioMan.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    AudioStreamState(currVol, minVol, maxVol, streamType)
                }
                AudioStreamType.RINGTONE -> {
                    val currVol = audioMan.getStreamVolume(AudioManager.STREAM_RING)
                    val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioMan.getStreamMinVolume(AudioManager.STREAM_RING) else 0
                    val maxVol = audioMan.getStreamMaxVolume(AudioManager.STREAM_RING)
                    AudioStreamState(currVol, minVol, maxVol, streamType)
                }
                AudioStreamType.VOICE_CALL -> {
                    val currVol = audioMan.getStreamVolume(AudioManager.STREAM_VOICE_CALL)
                    val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioMan.getStreamMinVolume(AudioManager.STREAM_VOICE_CALL) else 0
                    val maxVol = audioMan.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
                    AudioStreamState(currVol, minVol, maxVol, streamType)
                }
                AudioStreamType.ALARM -> {
                    val currVol = audioMan.getStreamVolume(AudioManager.STREAM_ALARM)
                    val minVol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioMan.getStreamMinVolume(AudioManager.STREAM_ALARM) else 0
                    val maxVol = audioMan.getStreamMaxVolume(AudioManager.STREAM_ALARM)
                    AudioStreamState(currVol, minVol, maxVol, streamType)
                }
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            null
        }
    }

    fun setVolume(context: Context, direction: ValueDirection?, streamType: AudioStreamType? = null): ActionStatus {
        return try {
            val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val powerMan = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val isInteractive = powerMan.isInteractive
            var flags = AudioManager.FLAG_PLAY_SOUND
            if (isInteractive) flags = flags or AudioManager.FLAG_SHOW_UI

            val audioStream = when (streamType) {
                AudioStreamType.MUSIC -> AudioManager.STREAM_MUSIC
                AudioStreamType.RINGTONE -> AudioManager.STREAM_RING
                AudioStreamType.VOICE_CALL -> AudioManager.STREAM_VOICE_CALL
                AudioStreamType.ALARM -> AudioManager.STREAM_ALARM
                null -> AudioManager.USE_DEFAULT_STREAM_TYPE
            }

            if (audioStream != AudioManager.USE_DEFAULT_STREAM_TYPE) {
                when (direction) {
                    ValueDirection.UP -> audioMan.adjustStreamVolume(audioStream, AudioManager.ADJUST_RAISE, flags)
                    ValueDirection.DOWN -> audioMan.adjustStreamVolume(audioStream, AudioManager.ADJUST_LOWER, flags)
                }
            } else {
                when (direction) {
                    ValueDirection.UP -> audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, flags)
                    ValueDirection.DOWN -> audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_LOWER, AudioManager.USE_DEFAULT_STREAM_TYPE, flags)
                }
            }

            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun isNotificationAccessAllowed(context: Context): Boolean {
        val notMan = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notMan.isNotificationPolicyAccessGranted
    }

    fun getDNDState(context: Context): DNDChoice {
        val notMan = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return when (notMan.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> DNDChoice.ALARMS
            NotificationManager.INTERRUPTION_FILTER_ALL -> DNDChoice.OFF
            NotificationManager.INTERRUPTION_FILTER_NONE -> DNDChoice.SILENCE
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> DNDChoice.PRIORITY
            NotificationManager.INTERRUPTION_FILTER_UNKNOWN -> DNDChoice.OFF
            else -> DNDChoice.OFF
        }
    }

    fun setDNDState(context: Context, enable: Boolean): ActionStatus {
        return setDNDState(context, if (enable) DNDChoice.PRIORITY else DNDChoice.OFF)
    }

    fun setDNDState(context: Context, dnd: DNDChoice?): ActionStatus {
        if (!isNotificationAccessAllowed(context)) return ActionStatus.PERMISSION_DENIED
        return try {
            val notMan = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            when (dnd) {
                DNDChoice.OFF -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                DNDChoice.PRIORITY -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                DNDChoice.ALARMS -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
                DNDChoice.SILENCE -> notMan.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE)
                else -> return ActionStatus.FAILURE
            }
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun getRingerState(context: Context): RingerChoice {
        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return when (audioMan.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingerChoice.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerChoice.VIBRATION
            AudioManager.RINGER_MODE_NORMAL -> RingerChoice.SOUND
            else -> RingerChoice.SOUND
        }
    }

    fun setRingerState(context: Context, ringer: RingerChoice?): ActionStatus {
        return try {
            val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            when (ringer) {
                RingerChoice.VIBRATION -> audioMan.ringerMode = AudioManager.RINGER_MODE_VIBRATE
                RingerChoice.SOUND -> audioMan.ringerMode = AudioManager.RINGER_MODE_NORMAL
                RingerChoice.SILENT -> audioMan.ringerMode = AudioManager.RINGER_MODE_SILENT
                else -> audioMan.ringerMode = AudioManager.RINGER_MODE_VIBRATE
            }
            ActionStatus.SUCCESS
        } catch (e: SecurityException) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.PERMISSION_DENIED
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun sendPlayMusicCommand(context: Context): ActionStatus {
        // Add a minor delay before sending the command
        runBlocking {
            delay(500)
        }

        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioMan.dispatchMediaKeyEvent(event)

        // Wait for a second to see if music plays
        val musicActive = runBlocking {
            delay(1000)
            audioMan.isMusicActive
        }
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    fun sendPlayMusicCommand(context: Context, playIntent: Intent): ActionStatus {
        // Add a minor delay before sending the command
        runBlocking {
            delay(500)
        }

        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        context.sendBroadcast(playIntent)

        // Wait for a second to see if music plays
        val musicActive = runBlocking {
            delay(1000)
            audioMan.isMusicActive
        }
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    fun isMusicActive(context: Context): ActionStatus {
        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Wait for a second to see if music plays
        val musicActive = runBlocking {
            delay(1000)
            audioMan.isMusicActive
        }
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    fun openMobileDataSettings(context: Context): ActionStatus {
        return try {
            context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }
}