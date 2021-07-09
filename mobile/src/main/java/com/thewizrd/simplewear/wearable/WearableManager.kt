package com.thewizrd.simplewear.wearable

import android.companion.CompanionDeviceManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import androidx.annotation.RestrictTo
import androidx.media.MediaBrowserServiceCompat
import com.google.android.gms.wearable.*
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.media.MediaAppControllerUtils
import com.thewizrd.simplewear.services.NotificationListener
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.*

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WearableManager(private val mContext: Context) : OnCapabilityChangedListener {
    init {
        init()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var mCapabilityClient: CapabilityClient
    private var mWearNodesWithApp: Collection<Node>? = null

    private fun init() {
        mCapabilityClient = Wearable.getCapabilityClient(mContext)
        mCapabilityClient.addListener(this, WearableHelper.CAPABILITY_WEAR_APP)
    }

    fun unregister() {
        scope.cancel()
        mCapabilityClient.removeListener(this)
    }

    suspend fun isWearNodesAvailable(): Boolean {
        if (mWearNodesWithApp == null) {
            mWearNodesWithApp = findWearDevicesWithApp()
        }
        return !mWearNodesWithApp.isNullOrEmpty()
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        mWearNodesWithApp = capabilityInfo.nodes
        scope.launch { requestWearAppState() }
    }

    private suspend fun findWearDevicesWithApp(): Collection<Node>? {
        var capabilityInfo: CapabilityInfo? = null
        try {
            capabilityInfo = mCapabilityClient.getCapability(
                WearableHelper.CAPABILITY_WEAR_APP,
                CapabilityClient.FILTER_ALL
            )
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
        return capabilityInfo?.nodes
    }

    suspend fun startMusicPlayer(nodeID: String?, pkgName: String, activityName: String?, playMusic: Boolean) {
        if (!pkgName.isNullOrBlank() && !activityName.isNullOrBlank()) {
            val appIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_APP_MUSIC)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component =
                    ComponentName(pkgName, activityName)
            }

            var playKeyIntent: Intent? = null

            if (playMusic) {
                // Check if the app has a registered MediaButton BroadcastReceiver
                val infos = mContext.packageManager.queryBroadcastReceivers(
                    Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkgName),
                    PackageManager.GET_RESOLVED_FILTER
                )
                for (info in infos) {
                    if (pkgName == info.activityInfo.packageName) {
                        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
                        playKeyIntent = Intent().apply {
                            putExtra(Intent.EXTRA_KEY_EVENT, event)
                            setAction(Intent.ACTION_MEDIA_BUTTON).component =
                                ComponentName(pkgName, info.activityInfo.name)
                        }
                        break
                    }
                }
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                try {
                    mContext.startActivity(appIntent)
                } catch (e: ActivityNotFoundException) {
                    sendMessage(
                        nodeID,
                        MediaHelper.PlayCommandPath,
                        ActionStatus.FAILURE.name.stringToBytes()
                    )
                    return
                }
                if (playMusic) {
                    // Give the system enough time to start the app
                    delay(4500)

                    // If the app has a registered MediaButton Broadcast receiver,
                    // Send the media keyevent directly to the app; Otherwise, send
                    // a broadcast to the most recent music session, which should be the
                    // app activity we just started
                    if (playKeyIntent != null) {
                        sendMessage(
                            nodeID,
                            MediaHelper.PlayCommandPath,
                            PhoneStatusHelper.sendPlayMusicCommand(
                                mContext,
                                playKeyIntent
                            ).name.stringToBytes()
                        )
                    } else {
                        sendMessage(
                            nodeID,
                            MediaHelper.PlayCommandPath,
                            PhoneStatusHelper.sendPlayMusicCommand(mContext).name.stringToBytes()
                        )
                    }
                }
            } else { // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                val deviceManager = mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                val associated_devices = deviceManager.associations
                if (associated_devices.isEmpty()) {
                    // No devices associated; send message to user
                    sendMessage(
                        nodeID,
                        MediaHelper.PlayCommandPath,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                } else {
                    try {
                        mContext.startActivity(appIntent)
                    } catch (e: ActivityNotFoundException) {
                        sendMessage(
                            nodeID,
                            MediaHelper.PlayCommandPath,
                            ActionStatus.FAILURE.name.stringToBytes()
                        )
                        return
                    }
                    if (playMusic) {
                        // Give the system enough time to start the app
                        delay(4500)

                        // If the app has a registered MediaButton Broadcast receiver,
                        // Send the media keyevent directly to the app; Otherwise, send
                        // a broadcast to the most recent music session, which should be the
                        // app activity we just started
                        if (playKeyIntent != null) {
                            sendMessage(
                                nodeID,
                                MediaHelper.PlayCommandPath,
                                PhoneStatusHelper.sendPlayMusicCommand(
                                    mContext,
                                    playKeyIntent
                                ).name.stringToBytes()
                            )
                        } else {
                            sendMessage(
                                nodeID,
                                MediaHelper.PlayCommandPath,
                                PhoneStatusHelper.sendPlayMusicCommand(mContext).name.stringToBytes()
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun sendSupportedMusicPlayers() {
        val mediaBrowserInfos = mContext.packageManager.queryIntentServices(
            Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE),
            PackageManager.GET_RESOLVED_FILTER
        )

        val activeMediaInfos = MediaAppControllerUtils.getMediaAppsFromControllers(
            mContext,
            MediaAppControllerUtils.getActiveMediaSessions(
                mContext,
                NotificationListener.getComponentName(mContext)
            )
        )

        val mapRequest = PutDataMapRequest.create(MediaHelper.MusicPlayersPath)

        // Sort result
        Collections.sort(
            mediaBrowserInfos,
            ResolveInfo.DisplayNameComparator(mContext.packageManager)
        )

        val supportedPlayers = ArrayList<String>(mediaBrowserInfos.size)

        fun addPlayerInfo(appInfo: ApplicationInfo) {
            val launchIntent =
                mContext.packageManager.getLaunchIntentForPackage(appInfo.packageName)
            if (launchIntent != null) {
                val activityInfo = mContext.packageManager.resolveActivity(
                    launchIntent,
                    PackageManager.MATCH_DEFAULT_ONLY
                )
                    ?: return
                val activityCmpName =
                    ComponentName(appInfo.packageName, activityInfo.activityInfo.name)
                val key =
                    String.format("%s/%s", appInfo.packageName, activityInfo.activityInfo.name)
                if (!supportedPlayers.contains(key)) {
                    val label = mContext.packageManager.getApplicationLabel(appInfo).toString()
                    var iconBmp: Bitmap? = null
                    try {
                        val iconDrwble = mContext.packageManager.getActivityIcon(activityCmpName)
                        val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, mContext.resources.displayMetrics).toInt()
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                    val map = DataMap()
                    map.putString(WearableHelper.KEY_LABEL, label)
                    map.putString(WearableHelper.KEY_PKGNAME, appInfo.packageName)
                    map.putString(WearableHelper.KEY_ACTIVITYNAME, activityInfo.activityInfo.name)
                    map.putAsset(
                        WearableHelper.KEY_ICON,
                        iconBmp?.let { ImageUtils.createAssetFromBitmap(iconBmp) })
                    mapRequest.dataMap.putDataMap(key, map)
                    supportedPlayers.add(key)
                }
            }
        }

        for (info in mediaBrowserInfos) {
            val appInfo = info.serviceInfo.applicationInfo
            addPlayerInfo(appInfo)
        }

        for (info in activeMediaInfos) {
            addPlayerInfo(info)
        }

        mapRequest.dataMap.putStringArrayList(MediaHelper.KEY_SUPPORTEDPLAYERS, supportedPlayers)
        mapRequest.setUrgent()
        try {
            Wearable.getDataClient(mContext)
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    suspend fun sendApps() {
        val infos = mContext.packageManager.getInstalledApplications(0)
        val mapRequest = PutDataMapRequest.create(WearableHelper.AppsPath)

        // Sort result
        Collections.sort(infos, ApplicationInfo.DisplayNameComparator(mContext.packageManager))

        val availableApps = ArrayList<String>(infos.size)

        for (info in infos) {
            val launchIntent = mContext.packageManager.getLaunchIntentForPackage(info.packageName)
                    ?: continue

            val activityCmpName = launchIntent.component ?: continue
            val key = String.format("%s/%s", info.packageName, activityCmpName.className)
            if (!availableApps.contains(key)) {
                val label = mContext.packageManager.getApplicationLabel(info).toString()
                var iconBmp: Bitmap? = null
                try {
                    val iconDrwble = mContext.packageManager.getActivityIcon(activityCmpName)
                    val size = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36f, mContext.resources.displayMetrics).toInt()
                    iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                } catch (ignored: PackageManager.NameNotFoundException) {
                }
                val map = DataMap()
                map.putString(WearableHelper.KEY_LABEL, label)
                map.putString(WearableHelper.KEY_PKGNAME, info.packageName)
                map.putString(WearableHelper.KEY_ACTIVITYNAME, activityCmpName.className)
                if (iconBmp != null) {
                    map.putAsset(WearableHelper.KEY_ICON, ImageUtils.createAssetFromBitmap(iconBmp))
                }
                mapRequest.dataMap.putDataMap(key, map)
                availableApps.add(key)
            }
        }
        mapRequest.dataMap.putStringArrayList(WearableHelper.KEY_APPS, availableApps)
        mapRequest.setUrgent()
        try {
            Wearable.getDataClient(mContext)
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    suspend fun launchApp(nodeID: String?, pkgName: String, activityName: String?) {
        if (!pkgName.isNullOrBlank() && !activityName.isNullOrBlank()) {
            val appIntent = Intent().apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                setFlags(Intent.FLAG_ACTIVITY_NEW_TASK).component =
                    ComponentName(pkgName, activityName)
            }

            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                try {
                    mContext.startActivity(appIntent)
                    sendMessage(
                        nodeID,
                        WearableHelper.LaunchAppPath,
                        ActionStatus.SUCCESS.name.stringToBytes()
                    )
                } catch (e: ActivityNotFoundException) {
                    sendMessage(
                        nodeID,
                        WearableHelper.LaunchAppPath,
                        ActionStatus.FAILURE.name.stringToBytes()
                    )
                }
            } else { // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                val deviceManager = mContext.getSystemService(Context.COMPANION_DEVICE_SERVICE) as CompanionDeviceManager
                val associated_devices = deviceManager.associations
                if (associated_devices.isEmpty()) {
                    // No devices associated; send message to user
                    sendMessage(nodeID, WearableHelper.LaunchAppPath, ActionStatus.PERMISSION_DENIED.name.stringToBytes())
                } else {
                    try {
                        mContext.startActivity(appIntent)
                        sendMessage(nodeID, WearableHelper.LaunchAppPath, ActionStatus.SUCCESS.name.stringToBytes())
                    } catch (e: ActivityNotFoundException) {
                        sendMessage(nodeID, WearableHelper.LaunchAppPath, ActionStatus.FAILURE.name.stringToBytes())
                    }
                }
            }
        }
    }

    suspend fun sendAudioModeStatus(nodeID: String?, streamType: AudioStreamType) {
        sendMessage(
            nodeID, WearableHelper.AudioStatusPath,
            JSONParser.serializer(
                PhoneStatusHelper.getStreamVolume(mContext, streamType),
                AudioStreamState::class.java
            )?.stringToBytes()
        )
    }

    suspend fun requestWearAppState() {
        if (mWearNodesWithApp == null) return
        for (node in mWearNodesWithApp!!) {
            sendMessage(node.id, WearableHelper.AppStatePath, null)
        }
    }

    suspend fun sendStatusUpdate(nodeID: String?, path: String?) {
        if (path != null && path.contains(WearableHelper.WifiPath)) {
            sendMessage(nodeID, path, byteArrayOf(PhoneStatusHelper.getWifiState(mContext).toByte()))
        } else if (path != null && path.contains(WearableHelper.BatteryPath)) {
            sendMessage(nodeID, path, JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(mContext), BatteryStatus::class.java).stringToBytes())
        } else if (path == null || WearableHelper.StatusPath == path) {
            // Status dump
            sendMessage(nodeID, WearableHelper.WifiPath, byteArrayOf(PhoneStatusHelper.getWifiState(mContext).toByte()))
            sendMessage(nodeID, WearableHelper.BatteryPath, JSONParser.serializer(PhoneStatusHelper.getBatteryLevel(mContext), BatteryStatus::class.java).stringToBytes())
        }
    }

    fun sendActionsUpdate(nodeID: String?) {
        scope.launch {
            for (act in Actions.values()) {
                async { sendActionsUpdate(nodeID, act) }
            }
        }
    }

    suspend fun sendActionsUpdate(nodeID: String?, act: Actions?) {
        val action: Action
        when (act) {
            Actions.WIFI -> {
                action = ToggleAction(act, PhoneStatusHelper.isWifiEnabled(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.BLUETOOTH -> {
                action = ToggleAction(act, PhoneStatusHelper.isBluetoothEnabled(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.MOBILEDATA -> {
                action = ToggleAction(act, PhoneStatusHelper.isMobileDataEnabled(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.LOCATION -> {
                action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    ToggleAction(act, PhoneStatusHelper.isLocationEnabled(mContext))
                } else {
                    MultiChoiceAction(act, PhoneStatusHelper.getLocationState(mContext).value)
                }
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.TORCH -> {
                action = ToggleAction(act, PhoneStatusHelper.isTorchEnabled(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.LOCKSCREEN, Actions.VOLUME -> {
            }
            Actions.DONOTDISTURB -> {
                action = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    MultiChoiceAction(act, PhoneStatusHelper.getDNDState(mContext).value)
                } else {
                    ToggleAction(act, PhoneStatusHelper.getDNDState(mContext) != DNDChoice.OFF)
                }
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
            Actions.RINGER -> {
                action = MultiChoiceAction(act, PhoneStatusHelper.getRingerState(mContext).value)
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(action, Action::class.java).stringToBytes())
            }
        }
    }

    suspend fun performAction(nodeID: String?, action: Action) {
        val tA: ToggleAction
        val nA: NormalAction
        val vA: ValueAction
        val mA: MultiChoiceAction
        when (action.actionType) {
            Actions.WIFI -> {
                tA = action as ToggleAction
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    /* WifiManager.setWifiEnabled is unavailable as of Android 10 */
                    tA.setActionSuccessful(PhoneStatusHelper.openWifiSettings(mContext))
                    tA.isEnabled = PhoneStatusHelper.isWifiEnabled(mContext)
                } else {
                    tA.setActionSuccessful(PhoneStatusHelper.setWifiEnabled(mContext, tA.isEnabled))
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(tA, Action::class.java).stringToBytes()
                )
            }
            Actions.BLUETOOTH -> {
                tA = action as ToggleAction
                tA.setActionSuccessful(PhoneStatusHelper.setBluetoothEnabled(mContext, tA.isEnabled))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(tA, Action::class.java).stringToBytes())
            }
            Actions.MOBILEDATA -> {
                tA = action as ToggleAction
                tA.isEnabled = PhoneStatusHelper.isMobileDataEnabled(mContext)
                tA.setActionSuccessful(PhoneStatusHelper.openMobileDataSettings(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(tA, Action::class.java).stringToBytes())
            }
            Actions.LOCATION -> {
                if (action is MultiChoiceAction) {
                    mA = action
                    mA.choice = PhoneStatusHelper.getLocationState(mContext).value
                    sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(mA, Action::class.java).stringToBytes())
                } else if (action is ToggleAction) {
                    tA = action
                    tA.isEnabled = PhoneStatusHelper.isLocationEnabled(mContext)
                    tA.setActionSuccessful(PhoneStatusHelper.openLocationSettings(mContext))
                    sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(tA, Action::class.java).stringToBytes())
                }
            }
            Actions.TORCH -> {
                tA = action as ToggleAction
                tA.setActionSuccessful(PhoneStatusHelper.setTorchEnabled(mContext, tA.isEnabled))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(tA, Action::class.java).stringToBytes())
            }
            Actions.LOCKSCREEN -> {
                nA = action as NormalAction
                nA.setActionSuccessful(PhoneStatusHelper.lockScreen(mContext))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(nA, Action::class.java).stringToBytes())
            }
            Actions.VOLUME -> {
                vA = action as ValueAction
                if (vA is VolumeAction) {
                    vA.setActionSuccessful(PhoneStatusHelper.setVolume(mContext, vA.direction, vA.streamType))
                    vA.streamType?.let {
                        scope.launch {
                            sendAudioModeStatus(nodeID, it)
                        }
                    }
                } else {
                    vA.setActionSuccessful(PhoneStatusHelper.setVolume(mContext, vA.direction))
                }
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(vA, Action::class.java).stringToBytes())
            }
            Actions.DONOTDISTURB -> {
                if (action is MultiChoiceAction) {
                    mA = action
                    mA.setActionSuccessful(PhoneStatusHelper.setDNDState(mContext, DNDChoice.valueOf(mA.choice)))
                    sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(mA, Action::class.java).stringToBytes())
                } else if (action is ToggleAction) {
                    tA = action
                    tA.setActionSuccessful(PhoneStatusHelper.setDNDState(mContext, tA.isEnabled))
                    sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(tA, Action::class.java).stringToBytes())
                }
            }
            Actions.RINGER -> {
                mA = action as MultiChoiceAction
                mA.setActionSuccessful(PhoneStatusHelper.setRingerState(mContext, RingerChoice.valueOf(mA.choice)))
                sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(mA, Action::class.java).stringToBytes())
            }
        }
    }

    suspend fun sendMessage(nodeID: String?, path: String, data: ByteArray?) {
        if (nodeID == null) {
            if (mWearNodesWithApp == null) {
                // Create requests if nodes exist with app support
                mWearNodesWithApp = findWearDevicesWithApp()
                if (mWearNodesWithApp == null || mWearNodesWithApp!!.isEmpty()) return
            }
        }
        if (nodeID != null) {
            try {
                Wearable.getMessageClient(mContext)
                    .sendMessage(nodeID, path, data)
                    .await()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        } else {
            for (node in mWearNodesWithApp!!) {
                try {
                    Wearable.getMessageClient(mContext)
                        .sendMessage(node.id, path, data)
                        .await()
                } catch (e: Exception) {
                    Logger.writeLine(Log.ERROR, e)
                }
            }
        }
    }
}