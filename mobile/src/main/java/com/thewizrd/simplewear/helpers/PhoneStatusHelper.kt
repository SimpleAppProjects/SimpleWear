package com.thewizrd.simplewear.helpers

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothManager
import android.companion.CompanionDeviceManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.RequiresApi
import androidx.core.app.PendingIntentCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.EXTRA_ACTION_DATA
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.actions.ValueDirection
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.ScreenLockAdminReceiver
import com.thewizrd.simplewear.camera.TorchListener
import com.thewizrd.simplewear.receivers.PhoneBroadcastReceiver
import com.thewizrd.simplewear.services.TorchService
import com.thewizrd.simplewear.services.TorchService.Companion.enqueueWork
import com.thewizrd.simplewear.services.WearAccessibilityService
import com.thewizrd.simplewear.utils.BrightnessUtils
import com.thewizrd.simplewear.utils.hasAssociations
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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

    @DeprecatedSinceApi(Build.VERSION_CODES.Q)
    fun setWifiEnabled(context: Context, enable: Boolean): ActionStatus {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CHANGE_WIFI_STATE
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            return try {
                val wifiMan =
                    context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
                if (wifiMan.setWifiEnabled(enable)) ActionStatus.SUCCESS else ActionStatus.FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        }
        return ActionStatus.PERMISSION_DENIED
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        val btService = context.applicationContext.getSystemService(BluetoothManager::class.java)
        return btService.adapter?.isEnabled ?: false
    }

    @DeprecatedSinceApi(Build.VERSION_CODES.TIRAMISU)
    fun setBluetoothEnabled(context: Context, enable: Boolean): ActionStatus {
        val btService = context.applicationContext.getSystemService(BluetoothManager::class.java)
        return btService.adapter?.let {
            try {
                val success = if (enable) it.enable() else it.disable()
                if (success) {
                    ActionStatus.SUCCESS
                } else {
                    ActionStatus.FAILURE
                }
            } catch (e: SecurityException) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.PERMISSION_DENIED
            }
        } ?: ActionStatus.FAILURE
    }

    @SuppressLint("MissingPermission")
    fun isMobileDataEnabled(context: Context): Boolean {
        return try {
            var telephonyManager = context.getSystemService(TelephonyManager::class.java)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val activeSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    SubscriptionManager.getActiveDataSubscriptionId().takeUnless {
                        it == SubscriptionManager.INVALID_SUBSCRIPTION_ID
                    } ?: SubscriptionManager.getDefaultSubscriptionId()
                } else {
                    SubscriptionManager.getDefaultSubscriptionId()
                }
                if (activeSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                    telephonyManager = telephonyManager.createForSubscriptionId(activeSubId)
                }

                telephonyManager.isDataEnabled
            } else {
                if (telephonyManager.phoneCount > 1) {
                    val activeSubId = SubscriptionManager.getDefaultSubscriptionId()

                    if (activeSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                        Settings.Global.getInt(
                            context.contentResolver,
                            "mobile_data${activeSubId}",
                            0
                        ) == 1
                    } else {
                        Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
                    }
                } else {
                    Settings.Global.getInt(context.contentResolver, "mobile_data", 0) == 1
                }
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            false
        }
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

    fun setLocationEnabled(context: Context, enable: Boolean): ActionStatus {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val success = Settings.Secure.putInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                if (enable) Settings.Secure.LOCATION_MODE_HIGH_ACCURACY else Settings.Secure.LOCATION_MODE_OFF
            )
            if (success) {
                ActionStatus.SUCCESS
            } else {
                ActionStatus.FAILURE
            }
        } else {
            ActionStatus.PERMISSION_DENIED
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

    fun setLocationState(context: Context, state: LocationState): ActionStatus {
        return if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_SECURE_SETTINGS
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            val success = Settings.Secure.putInt(
                context.contentResolver, Settings.Secure.LOCATION_MODE,
                when (state) {
                    LocationState.OFF -> Settings.Secure.LOCATION_MODE_OFF
                    LocationState.SENSORS_ONLY -> Settings.Secure.LOCATION_MODE_SENSORS_ONLY
                    LocationState.BATTERY_SAVING -> Settings.Secure.LOCATION_MODE_BATTERY_SAVING
                    LocationState.HIGH_ACCURACY -> Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                }
            )
            if (success) {
                ActionStatus.SUCCESS
            } else {
                ActionStatus.FAILURE
            }
        } else {
            ActionStatus.PERMISSION_DENIED
        }
    }

    fun isCameraPermissionEnabled(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun isTorchEnabled(context: Context): Boolean {
        return isServiceRunning(context, TorchService::class.java) || TorchListener.isTorchEnabled
    }

    private suspend fun isSystemTorchEnabled(context: Context): Boolean {
        return try {
            suspendCancellableCoroutine { continuation ->
                val cameraMgr = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val primaryCameraId = cameraMgr.cameraIdList[0]

                val torchCallback = object : CameraManager.TorchCallback() {
                    override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                        if (cameraId == primaryCameraId) {
                            if (continuation.isActive) {
                                continuation.resume(enabled)
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    cameraMgr.unregisterTorchCallback(torchCallback)
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    cameraMgr.registerTorchCallback(
                        Executors.newSingleThreadExecutor(),
                        torchCallback
                    )
                } else {
                    cameraMgr.registerTorchCallback(torchCallback, Handler(Looper.getMainLooper()))
                }
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            false
        }
    }

    fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
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
        val mDPM =
            context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val mScreenLockAdmin =
            ComponentName(context.applicationContext, ScreenLockAdminReceiver::class.java)
        return mDPM.isAdminActive(mScreenLockAdmin)
    }

    fun deActivateDeviceAdmin(context: Context) {
        val mDPM =
            context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val mScreenLockAdmin =
            ComponentName(context.applicationContext, ScreenLockAdminReceiver::class.java)
        mDPM.removeActiveAdmin(mScreenLockAdmin)
    }

    fun lockScreen(context: Context): ActionStatus {
        if (isDeviceAdminEnabled(context)) {
            return try {
                val mDPM =
                    context.applicationContext.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
                mDPM.lockNow()
                ActionStatus.SUCCESS
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && WearAccessibilityService.isServiceBound()) {
            val success = WearAccessibilityService.getInstance()
                ?.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)

            return if (success == true) ActionStatus.SUCCESS else ActionStatus.FAILURE
        }

        return ActionStatus.PERMISSION_DENIED
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
                    ValueDirection.DOWN -> audioMan.adjustStreamVolume(
                        audioStream,
                        AudioManager.ADJUST_LOWER,
                        flags
                    )

                    else -> {}
                }
            } else {
                when (direction) {
                    ValueDirection.UP -> audioMan.adjustSuggestedStreamVolume(AudioManager.ADJUST_RAISE, AudioManager.USE_DEFAULT_STREAM_TYPE, flags)
                    ValueDirection.DOWN -> audioMan.adjustSuggestedStreamVolume(
                        AudioManager.ADJUST_LOWER,
                        AudioManager.USE_DEFAULT_STREAM_TYPE,
                        flags
                    )

                    else -> {}
                }
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

    fun setStreamVolume(context: Context, volume: Int, streamType: AudioStreamType): ActionStatus {
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
            }

            audioMan.setStreamVolume(audioStream, volume, flags)

            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun isNotificationAccessAllowed(context: Context): Boolean {
        val notMan =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return notMan.isNotificationPolicyAccessGranted
    }

    fun getDNDState(context: Context): DNDChoice {
        val notMan =
            context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
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
                RingerChoice.SILENT -> {
                    val dndState = getDNDState(context)

                    audioMan.ringerMode = AudioManager.RINGER_MODE_SILENT
                    audioMan.adjustStreamVolume(
                        AudioManager.STREAM_RING,
                        AudioManager.ADJUST_MUTE,
                        AudioManager.FLAG_REMOVE_SOUND_AND_VIBRATE
                    )

                    /*
                     * Setting ringerMode to silent may trigger Do Not Disturb mode to change
                     * In case this happens, set it back to its original state
                     */
                    setDNDState(context, dndState)
                }
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

    suspend fun sendPlayMusicCommand(context: Context): ActionStatus {
        // Add a minor delay before sending the command
        delay(500)

        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioMan.dispatchMediaKeyEvent(event)

        // Wait for a second to see if music plays
        delay(1000)
        val musicActive = audioMan.isMusicActive
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    suspend fun sendPlayMusicCommand(context: Context, playIntent: Intent): ActionStatus {
        // Add a minor delay before sending the command
        delay(500)

        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        runCatching {
            context.applicationContext.sendBroadcast(playIntent)

            // Wait for a second to see if music plays
            delay(1000)
        }.onFailure {
            Logger.writeLine(Log.ERROR, it)
        }

        val musicActive = audioMan.isMusicActive
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    suspend fun isMusicActive(context: Context, delay: Boolean = true): ActionStatus {
        val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Wait for a second to see if music plays
        if (delay) delay(1000)
        val musicActive = audioMan.isMusicActive
        return if (musicActive) ActionStatus.SUCCESS else ActionStatus.FAILURE
    }

    fun muteMicrophone(context: Context, mute: Boolean): ActionStatus {
        return try {
            val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioMan.isMicrophoneMute = mute
            ActionStatus.SUCCESS
        } catch (e: SecurityException) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.PERMISSION_DENIED
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun setSpeakerphoneOn(context: Context, on: Boolean): ActionStatus {
        return try {
            val audioMan = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioMan.isSpeakerphoneOn = on
            ActionStatus.SUCCESS
        } catch (e: SecurityException) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.PERMISSION_DENIED
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun companionDeviceAssociated(context: Context): Boolean {
        val deviceManager =
            context.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
        return deviceManager.hasAssociations()
    }

    fun callStatePermissionEnabled(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun openMobileDataSettings(context: Context): ActionStatus {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.startActivity(
                    Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }

            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun openWifiSettings(context: Context): ActionStatus {
        return try {
            context.startActivity(
                Intent(Settings.Panel.ACTION_WIFI)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun openLocationSettings(context: Context): ActionStatus {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun openBTSettings(context: Context): ActionStatus {
        return try {
            context.startActivity(
                Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun isWriteSystemSettingsPermissionEnabled(context: Context): Boolean {
        return Settings.System.canWrite(context)
    }

    @SuppressLint("DiscouragedPrivateApi")
    private fun getSystemBrightnessLevel(context: Context): ValueActionState {
        val powerMan = context.getSystemService(Context.POWER_SERVICE) as PowerManager

        val contentResolver = context.applicationContext.contentResolver
        val brightnessLevel =
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 0)
        val brightnessState = ValueActionState(brightnessLevel, 0, 255, Actions.BRIGHTNESS)

        runCatching {
            val getMaxMethod =
                powerMan.javaClass.getDeclaredMethod("getMaximumScreenBrightnessSetting")
            //val getMinMethod = powerMan.javaClass.getDeclaredMethod("getMinimumScreenBrightnessSetting")

            val max = getMaxMethod.invoke(powerMan) as Int
            //val min = getMinMethod.invoke(powerMan) as Int

            brightnessState.maxValue = max
            //brightnessState.minValue = min
        }

        return brightnessState
    }

    fun getBrightnessLevel(context: Context): ValueActionState {
        val systemBrightness = getSystemBrightnessLevel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val gammaValue = BrightnessUtils.convertLinearToGamma(
                systemBrightness.currentValue,
                systemBrightness.minValue,
                systemBrightness.maxValue
            )
            return ValueActionState(
                gammaValue,
                BrightnessUtils.GAMMA_SPACE_MIN,
                BrightnessUtils.GAMMA_SPACE_MAX,
                Actions.BRIGHTNESS
            )
        } else {
            return systemBrightness
        }
    }

    fun setBrightnessLevel(context: Context, value: Int): ActionStatus {
        if (isWriteSystemSettingsPermissionEnabled(context)) {
            return try {
                val brightnessLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val systemBrightness = getSystemBrightnessLevel(context)
                    BrightnessUtils.convertGammaToLinear(
                        value,
                        systemBrightness.minValue,
                        systemBrightness.maxValue
                    )
                } else {
                    value
                }

                val contentResolver = context.applicationContext.contentResolver
                val retVal = Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    brightnessLevel
                )
                if (retVal) ActionStatus.SUCCESS else ActionStatus.FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        }
        return ActionStatus.PERMISSION_DENIED
    }

    private fun setBrightnessLevel(context: Context, value: Float): ActionStatus {
        if (isWriteSystemSettingsPermissionEnabled(context)) {
            return try {
                val contentResolver = context.applicationContext.contentResolver
                val retVal = Settings.System.putInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS,
                    value.roundToInt()
                )
                if (retVal) ActionStatus.SUCCESS else ActionStatus.FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        }
        return ActionStatus.PERMISSION_DENIED
    }

    fun setBrightnessLevel(context: Context, direction: ValueDirection): ActionStatus {
        if (isWriteSystemSettingsPermissionEnabled(context)) {
            return try {
                val contentResolver = context.applicationContext.contentResolver
                val currentBrightness =
                    Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)

                // Increase/decrease by 5%
                val value = when (direction) {
                    ValueDirection.UP -> min(
                        255f,
                        max(0f, (currentBrightness + (255 * 0.05f)))
                    )
                    ValueDirection.DOWN -> min(
                        255f,
                        max(0f, (currentBrightness - (255 * 0.05f)))
                    )
                }

                return setBrightnessLevel(context, value)
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        }
        return ActionStatus.PERMISSION_DENIED
    }

    fun isAutoBrightnessEnabled(context: Context): Boolean {
        val contentResolver = context.applicationContext.contentResolver
        val value = Settings.System.getInt(
            contentResolver,
            Settings.System.SCREEN_BRIGHTNESS_MODE,
            Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
        )
        return value == 1
    }

    fun setAutoBrightnessEnabled(context: Context, enable: Boolean): ActionStatus {
        if (isWriteSystemSettingsPermissionEnabled(context)) {
            return try {
                val contentResolver = context.applicationContext.contentResolver
                val retVal = Settings.System.putInt(
                    contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE,
                    if (enable) {
                        Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                    } else {
                        Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
                    }
                )
                if (retVal) ActionStatus.SUCCESS else ActionStatus.FAILURE
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
                ActionStatus.FAILURE
            }
        }
        return ActionStatus.PERMISSION_DENIED
    }

    fun isBluetoothConnectPermGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmMgr =
                context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmMgr.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun scheduleTimedAction(context: Context, action: TimedAction): ActionStatus {
        if (!canScheduleExactAlarms(context))
            return ActionStatus.PERMISSION_DENIED

        try {
            // Check permissions
            when (action.action.actionType) {
                Actions.WIFI -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.CHANGE_WIFI_STATE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return ActionStatus.PERMISSION_DENIED
                        }
                    }
                }

                Actions.BLUETOOTH -> {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return ActionStatus.PERMISSION_DENIED
                        }
                    }
                }

                Actions.TORCH -> {
                    if (!isCameraPermissionEnabled(context)) {
                        return ActionStatus.PERMISSION_DENIED
                    }
                }

                Actions.DONOTDISTURB -> {
                    if (!isNotificationAccessAllowed(context)) {
                        return ActionStatus.PERMISSION_DENIED
                    }
                }

                else -> {}
            }

            // Schedule action
            return scheduleAction(context, action.timeInMillis, action.action)
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            return ActionStatus.FAILURE
        }
    }

    @SuppressLint("MissingPermission")
    private fun scheduleAction(context: Context, timeInMillis: Long, action: Action): ActionStatus {
        return try {
            if (canScheduleExactAlarms(context)) {
                val alarmMgr =
                    context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

                val actionData = JSONParser.serializer(action, Action::class.java)
                val intent =
                    Intent(context.applicationContext, PhoneBroadcastReceiver::class.java).apply {
                        setAction(PhoneBroadcastReceiver.ACTION_PERFORM_TIMED_ACTION)
                        putExtra(EXTRA_ACTION_DATA, actionData)
                    }
                val piFlags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
                val pi = PendingIntentCompat.getBroadcast(
                    context,
                    action.actionType.value,
                    intent,
                    piFlags,
                    false
                )!!

                alarmMgr.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timeInMillis, pi)

                // Save alarm
                AlarmStateManager(context.applicationContext).run {
                    saveAlarm(action.actionType, TimedAction(timeInMillis, action))
                }

                ActionStatus.SUCCESS
            } else {
                ActionStatus.PERMISSION_DENIED
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun removedScheduledTimedAction(context: Context, action: Actions): ActionStatus {
        return try {
            val alarmMgr =
                context.applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

            val intent =
                Intent(context.applicationContext, PhoneBroadcastReceiver::class.java).apply {
                    setAction(PhoneBroadcastReceiver.ACTION_PERFORM_TIMED_ACTION)
                }
            val piFlags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT
            val pi =
                PendingIntentCompat.getBroadcast(context, action.value, intent, piFlags, false)!!

            alarmMgr.cancel(pi)

            // Remove alarm state
            AlarmStateManager(context.applicationContext).run {
                clearAlarm(action)
            }

            ActionStatus.SUCCESS
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.FAILURE
        }
    }

    fun isWifiApEnabled(context: Context): Boolean = TetherHelper.isWifiApEnabled(context)

    fun setWifiApEnabled(context: Context, enable: Boolean): ActionStatus =
        TetherHelper.setWifiApEnabled(context, enable)
}