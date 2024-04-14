package com.thewizrd.simplewear.wearable

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.KeyEvent
import androidx.annotation.RestrictTo
import androidx.media.MediaBrowserServiceCompat
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.google.gson.stream.JsonWriter
import com.thewizrd.shared_resources.actions.ACTION_PERFORMACTION
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamState
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.EXTRA_ACTION_CALLINGPKG
import com.thewizrd.shared_resources.actions.EXTRA_ACTION_DATA
import com.thewizrd.shared_resources.actions.EXTRA_ACTION_ERROR
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RemoteActionReceiver
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.actions.ValueAction
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.actions.VolumeAction
import com.thewizrd.shared_resources.actions.toRemoteAction
import com.thewizrd.shared_resources.helpers.AppItemData
import com.thewizrd.shared_resources.helpers.AppItemSerializer.serialize
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearSettingsHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.ImageUtils.toAsset
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.shared_resources.wearsettings.PackageValidator
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.helpers.ResolveInfoActivityInfoComparator
import com.thewizrd.simplewear.media.MediaAppControllerUtils
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.Collections

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WearableManager(private val mContext: Context) : OnCapabilityChangedListener,
    RemoteActionReceiver.IResultReceiver {
    init {
        init()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var mCapabilityClient: CapabilityClient
    private var mWearNodesWithApp: Collection<Node>? = null

    private lateinit var mResultReceiver: RemoteActionReceiver
    private var mPackageValidator = PackageValidator(mContext)

    private fun init() {
        mCapabilityClient = Wearable.getCapabilityClient(mContext)
        mCapabilityClient.addListener(this, WearableHelper.CAPABILITY_WEAR_APP)

        mResultReceiver = RemoteActionReceiver().apply {
            resultReceiver = this@WearableManager
        }
    }

    fun unregister() {
        scope.cancel()
        mCapabilityClient.removeListener(this)
        mResultReceiver.resultReceiver = null
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
                            component =
                                ComponentName(info.activityInfo.packageName, info.activityInfo.name)
                            action = Intent.ACTION_MEDIA_BUTTON
                            putExtra(Intent.EXTRA_KEY_EVENT, event)
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
            } else {
                // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                // OR if SYSTEM_ALERT_WINDOW is granted
                if (!PhoneStatusHelper.companionDeviceAssociated(mContext) && !android.provider.Settings.canDrawOverlays(
                        mContext
                    )
                ) {
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
        val dataClient = Wearable.getDataClient(mContext)

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

        suspend fun addPlayerInfo(appInfo: ApplicationInfo) {
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
                        val size = TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            24f,
                            mContext.resources.displayMetrics
                        ).toInt()
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }
                    val map = DataMap()
                    map.putString(WearableHelper.KEY_LABEL, label)
                    map.putString(WearableHelper.KEY_PKGNAME, appInfo.packageName)
                    map.putString(WearableHelper.KEY_ACTIVITYNAME, activityInfo.activityInfo.name)
                    iconBmp?.let {
                        map.putAsset(WearableHelper.KEY_ICON, it.toAsset())
                    }
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
            dataClient.deleteDataItems(mapRequest.uri).await()
            dataClient
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    suspend fun sendApps(nodeID: String) {
        if (Settings.isLoadAppIcons()) {
            sendAppsViaChannelWithData(nodeID)
        } else {
            sendAppsViaData()
        }
    }

    private suspend fun sendAppsViaData() {
        val dataClient = Wearable.getDataClient(mContext)
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val infos = mContext.packageManager.queryIntentActivities(mainIntent, 0)
        val mapRequest = PutDataMapRequest.create(WearableHelper.AppsPath)

        // Sort result
        infos.sortWith(ResolveInfoActivityInfoComparator(mContext.packageManager))

        val availableApps = ArrayList<String>(infos.size)

        for (info in infos) {
            val key = String.format("%s/%s", info.activityInfo.packageName, info.activityInfo.name)
            if (!availableApps.contains(key)) {
                val label =
                    mContext.packageManager.getApplicationLabel(info.activityInfo.applicationInfo)
                        .toString()
                val map = DataMap()
                map.putString(WearableHelper.KEY_LABEL, label)
                map.putString(WearableHelper.KEY_PKGNAME, info.activityInfo.packageName)
                map.putString(WearableHelper.KEY_ACTIVITYNAME, info.activityInfo.name)
                mapRequest.dataMap.putDataMap(key, map)
                availableApps.add(key)
            }
        }
        mapRequest.dataMap.putStringArrayList(WearableHelper.KEY_APPS, availableApps)
        mapRequest.setUrgent()
        try {
            dataClient.deleteDataItems(mapRequest.uri).await()
            dataClient
                .putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    private suspend fun sendAppsViaChannel(nodeID: String) {
        val channelClient = Wearable.getChannelClient(mContext)
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val infos = mContext.packageManager.queryIntentActivities(mainIntent, 0)
        val appItems = mutableListOf<AppItemData>()

        // Sort result
        infos.sortWith(ResolveInfoActivityInfoComparator(mContext.packageManager))

        val availableApps = ArrayList<String>(infos.size)

        for (info in infos) {
            val key = String.format("%s/%s", info.activityInfo.packageName, info.activityInfo.name)
            if (!availableApps.contains(key)) {
                val label =
                    mContext.packageManager.getApplicationLabel(info.activityInfo.applicationInfo)
                        .toString()
                var iconBmp: Bitmap? = null
                try {
                    val iconDrwble =
                        info.activityInfo.applicationInfo.loadIcon(mContext.packageManager)
                    val size = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        24f,
                        mContext.resources.displayMetrics
                    ).toInt()
                    iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                } catch (ignored: PackageManager.NameNotFoundException) {
                }

                appItems.add(
                    AppItemData(
                        label,
                        info.activityInfo.packageName,
                        info.activityInfo.name,
                        iconBmp?.toByteArray()
                    )
                )
            }
        }

        try {
            withContext(Dispatchers.IO) {
                val channel = channelClient.openChannel(nodeID, WearableHelper.AppsPath).await()
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.use {
                    val writer = JsonWriter(BufferedWriter(OutputStreamWriter(it)))
                    appItems.serialize(writer)
                    writer.flush()
                }
                channelClient.close(channel)
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    private suspend fun sendAppsViaChannelWithData(nodeID: String) {
        val dataClient = Wearable.getDataClient(mContext)
        val channelClient = Wearable.getChannelClient(mContext)
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val infos = mContext.packageManager.queryIntentActivities(mainIntent, 0)
        val mapRequest = PutDataMapRequest.create(WearableHelper.AppsPath)

        // Sort result
        infos.sortWith(ResolveInfoActivityInfoComparator(mContext.packageManager))

        val availableApps = ArrayList<String>(infos.size)
        val appItems = ArrayList<AppItemData>(infos.size)

        for (info in infos) {
            val key = String.format("%s/%s", info.activityInfo.packageName, info.activityInfo.name)
            if (!availableApps.contains(key)) {
                val label = info.activityInfo.loadLabel(mContext.packageManager)
                var iconBmp: Bitmap? = null
                try {
                    val iconDrwble =
                        info.activityInfo.loadIcon(mContext.packageManager)
                    val size = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        24f,
                        mContext.resources.displayMetrics
                    ).toInt()
                    iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                } catch (ignored: PackageManager.NameNotFoundException) {
                }

                appItems.add(
                    AppItemData(
                        label.toString(),
                        info.activityInfo.packageName,
                        info.activityInfo.name,
                        iconBmp?.toByteArray()
                    )
                )

                val map = DataMap()
                map.putString(WearableHelper.KEY_LABEL, label.toString())
                map.putString(WearableHelper.KEY_PKGNAME, info.activityInfo.packageName)
                map.putString(WearableHelper.KEY_ACTIVITYNAME, info.activityInfo.name)
                mapRequest.dataMap.putDataMap(key, map)
                availableApps.add(key)
            }
        }

        val job1 = scope.async(Dispatchers.IO) {
            mapRequest.dataMap.putStringArrayList(WearableHelper.KEY_APPS, availableApps)
            mapRequest.setUrgent()
            try {
                dataClient.deleteDataItems(mapRequest.uri).await()
                dataClient
                    .putDataItem(mapRequest.asPutDataRequest())
                    .await()
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }

        val job2 = scope.async(Dispatchers.IO) {
            try {
                val channel = channelClient.openChannel(nodeID, WearableHelper.AppsPath).await()
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.use {
                    val writer = JsonWriter(BufferedWriter(OutputStreamWriter(it)))
                    appItems.serialize(writer)
                    writer.flush()
                }
                channelClient.close(channel)
            } catch (e: Exception) {
                Logger.writeLine(Log.ERROR, e)
            }
        }

        awaitAll(job1, job2)
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
                } catch (e: Exception) {
                    sendMessage(
                        nodeID,
                        WearableHelper.LaunchAppPath,
                        ActionStatus.FAILURE.name.stringToBytes()
                    )
                    if (e !is ActivityNotFoundException) {
                        Logger.writeLine(Log.ERROR, e)
                    }
                }
            } else {
                // Android Q+ Devices
                // Android Q puts a limitation on starting activities from the background
                // We are allowed to bypass this if we have a device registered as companion,
                // which will be our WearOS device; Check if device is associated before we start
                // OR if SYSTEM_ALERT_WINDOW is granted
                if (!PhoneStatusHelper.companionDeviceAssociated(mContext) && !android.provider.Settings.canDrawOverlays(
                        mContext
                    )
                ) {
                    // No devices associated; send message to user
                    sendMessage(
                        nodeID,
                        WearableHelper.LaunchAppPath,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                } else {
                    try {
                        mContext.startActivity(appIntent)
                        sendMessage(
                            nodeID,
                            WearableHelper.LaunchAppPath,
                            ActionStatus.SUCCESS.name.stringToBytes()
                        )
                    } catch (e: Exception) {
                        sendMessage(
                            nodeID,
                            WearableHelper.LaunchAppPath,
                            ActionStatus.FAILURE.name.stringToBytes()
                        )
                        if (e !is ActivityNotFoundException) {
                            Logger.writeLine(Log.ERROR, e)
                        }
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

    private var mValueJob: Job? = null
    suspend fun setStreamVolume(nodeID: String?, streamState: AudioStreamState) {
        mValueJob?.cancel()
        mValueJob = scope.launch {
            if (!isActive) return@launch

            val status = PhoneStatusHelper.setStreamVolume(
                mContext,
                streamState.currentVolume,
                streamState.streamType
            )
            if (status != ActionStatus.SUCCESS) {
                sendMessage(nodeID, WearableHelper.AudioVolumePath, status.name.stringToBytes())
            } else {
                sendAudioModeStatus(nodeID, streamState.streamType)
            }
        }
    }

    suspend fun setActionValue(nodeID: String?, valueData: ValueActionState) {
        mValueJob?.cancel()
        mValueJob = scope.launch {
            when (valueData.actionType) {
                Actions.VOLUME -> {
                    if (valueData is AudioStreamState) {
                        if (!isActive) return@launch

                        val status = PhoneStatusHelper.setStreamVolume(
                            mContext,
                            valueData.currentVolume,
                            valueData.streamType
                        )
                        if (status != ActionStatus.SUCCESS) {
                            sendMessage(
                                nodeID,
                                WearableHelper.ValueStatusSetPath,
                                status.name.stringToBytes()
                            )
                        } else {
                            sendAudioModeStatus(nodeID, valueData.streamType)
                        }
                    }
                }
                Actions.BRIGHTNESS -> {
                    if (!isActive) return@launch

                    val status = PhoneStatusHelper.setBrightnessLevel(
                        mContext,
                        valueData.currentValue
                    )
                    if (status != ActionStatus.SUCCESS) {
                        sendMessage(
                            nodeID,
                            WearableHelper.ValueStatusSetPath,
                            status.name.stringToBytes()
                        )
                    } else {
                        sendValueStatus(nodeID, valueData.actionType)
                    }
                }

                else -> {}
            }
        }
    }

    suspend fun sendValueStatus(nodeID: String?, actionType: Actions) {
        when (actionType) {
            Actions.BRIGHTNESS -> {
                sendMessage(
                    nodeID, WearableHelper.ValueStatusPath,
                    JSONParser.serializer(
                        PhoneStatusHelper.getBrightnessLevel(mContext),
                        ValueActionState::class.java
                    ).stringToBytes()
                )
                sendMessage(
                    nodeID, WearableHelper.BrightnessModePath,
                    PhoneStatusHelper.isAutoBrightnessEnabled(mContext).booleanToBytes()
                )
            }

            else -> {}
        }
    }

    suspend fun toggleBrightnessMode(nodeID: String?) {
        val autoEnabled = PhoneStatusHelper.isAutoBrightnessEnabled(mContext)
        val actionStatus = PhoneStatusHelper.setAutoBrightnessEnabled(mContext, !autoEnabled)
        if (actionStatus == ActionStatus.SUCCESS) {
            sendMessage(nodeID, WearableHelper.BrightnessModePath, (!autoEnabled).booleanToBytes())
        }
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
            for (act in Actions.entries) {
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
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(action, Action::class.java).stringToBytes()
                )
            }
            Actions.LOCKSCREEN, Actions.VOLUME, Actions.BRIGHTNESS -> {
            }
            Actions.DONOTDISTURB -> {
                action = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    MultiChoiceAction(act, PhoneStatusHelper.getDNDState(mContext).value)
                } else {
                    ToggleAction(act, PhoneStatusHelper.getDNDState(mContext) != DNDChoice.OFF)
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(action, Action::class.java).stringToBytes()
                )
            }
            Actions.RINGER -> {
                action = MultiChoiceAction(act, PhoneStatusHelper.getRingerState(mContext).value)
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(action, Action::class.java).stringToBytes()
                )
            }

            Actions.HOTSPOT -> {
                action = ToggleAction(act, PhoneStatusHelper.isWifiApEnabled(mContext))
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(action, Action::class.java).stringToBytes()
                )
            }

            else -> {}
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
                    if (WearSettingsHelper.isWearSettingsInstalled()) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            tA.setActionSuccessful(status)
                            WearSettingsHelper.launchWearSettings()
                        }
                    } else {
                        /* WifiManager.setWifiEnabled is unavailable as of Android 10 */
                        tA.setActionSuccessful(PhoneStatusHelper.openWifiSettings(mContext))
                        tA.isEnabled = PhoneStatusHelper.isWifiEnabled(mContext)
                    }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (WearSettingsHelper.isWearSettingsInstalled()) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            tA.setActionSuccessful(status)
                            WearSettingsHelper.launchWearSettings()
                        }
                    } else {
                        /* BluetoothAdapter.enable/disable is no-op as of Android 13 */
                        tA.setActionSuccessful(PhoneStatusHelper.openBTSettings(mContext))
                        tA.isEnabled = PhoneStatusHelper.isBluetoothEnabled(mContext)
                    }
                } else {
                    tA.setActionSuccessful(
                        PhoneStatusHelper.setBluetoothEnabled(
                            mContext,
                            tA.isEnabled
                        )
                    )
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(tA, Action::class.java).stringToBytes()
                )
            }
            Actions.MOBILEDATA -> {
                tA = action as ToggleAction
                if (WearSettingsHelper.isWearSettingsInstalled()) {
                    val status = performRemoteAction(action)
                    if (status == ActionStatus.REMOTE_FAILURE ||
                        status == ActionStatus.REMOTE_PERMISSION_DENIED
                    ) {
                        tA.setActionSuccessful(status)
                        WearSettingsHelper.launchWearSettings()
                    }
                } else {
                    tA.setActionSuccessful(PhoneStatusHelper.openMobileDataSettings(mContext))
                    tA.isEnabled = PhoneStatusHelper.isMobileDataEnabled(mContext)
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(tA, Action::class.java).stringToBytes()
                )
            }
            Actions.LOCATION -> {
                if (action is MultiChoiceAction) {
                    mA = action
                    if (WearSettingsHelper.isWearSettingsInstalled()) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            mA.setActionSuccessful(status)
                            WearSettingsHelper.launchWearSettings()
                        }
                    } else {
                        mA.setActionSuccessful(PhoneStatusHelper.openLocationSettings(mContext))
                        mA.choice = PhoneStatusHelper.getLocationState(mContext).value
                    }
                    sendMessage(
                        nodeID,
                        WearableHelper.ActionsPath,
                        JSONParser.serializer(mA, Action::class.java).stringToBytes()
                    )
                } else if (action is ToggleAction) {
                    tA = action
                    if (WearSettingsHelper.isWearSettingsInstalled()) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            tA.setActionSuccessful(status)
                            WearSettingsHelper.launchWearSettings()
                        }
                    } else {
                        tA.setActionSuccessful(PhoneStatusHelper.openLocationSettings(mContext))
                        tA.isEnabled = PhoneStatusHelper.isLocationEnabled(mContext)
                    }
                    sendMessage(
                        nodeID,
                        WearableHelper.ActionsPath,
                        JSONParser.serializer(tA, Action::class.java).stringToBytes()
                    )
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
                    sendMessage(
                        nodeID,
                        WearableHelper.ActionsPath,
                        JSONParser.serializer(tA, Action::class.java).stringToBytes()
                    )
                }
            }
            Actions.RINGER -> {
                mA = action as MultiChoiceAction
                mA.setActionSuccessful(
                    PhoneStatusHelper.setRingerState(
                        mContext,
                        RingerChoice.valueOf(mA.choice)
                    )
                )
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(mA, Action::class.java).stringToBytes()
                )
            }
            Actions.BRIGHTNESS -> {
                vA = action as ValueAction
                vA.setActionSuccessful(PhoneStatusHelper.setBrightnessLevel(mContext, vA.direction))
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(vA, Action::class.java).stringToBytes()
                )
                scope.launch {
                    sendValueStatus(nodeID, vA.actionType)
                }
            }

            Actions.HOTSPOT -> {
                tA = action as ToggleAction
                if (WearSettingsHelper.isWearSettingsInstalled() && Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    val status = performRemoteAction(action)
                    if (status == ActionStatus.REMOTE_FAILURE ||
                        status == ActionStatus.REMOTE_PERMISSION_DENIED
                    ) {
                        tA.setActionSuccessful(
                            PhoneStatusHelper.setWifiApEnabled(
                                mContext,
                                tA.isEnabled
                            )
                        )
                    }
                } else {
                    tA.setActionSuccessful(
                        PhoneStatusHelper.setWifiApEnabled(
                            mContext,
                            tA.isEnabled
                        )
                    )
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(tA, Action::class.java).stringToBytes()
                )
            }

            else -> {}
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

    private fun performRemoteAction(action: Action): ActionStatus {
        return try {
            if (mPackageValidator.isKnownCaller(WearSettingsHelper.getPackageName())) {
                mContext.startService(
                    Intent(ACTION_PERFORMACTION).apply {
                        component = WearSettingsHelper.getSettingsServiceComponent()
                        putExtra(
                            EXTRA_ACTION_DATA,
                            action.toRemoteAction(mResultReceiver)
                        )
                        putExtra(
                            EXTRA_ACTION_CALLINGPKG,
                            mContext.packageName
                        )
                    }
                )
                ActionStatus.UNKNOWN
            } else {
                throw IllegalStateException("Package: ${WearSettingsHelper.getPackageName()} has invalid certificate")
            }
        } catch (ise: IllegalStateException) {
            // Likely background service restriction
            Logger.writeLine(Log.ERROR, ise)
            ActionStatus.REMOTE_PERMISSION_DENIED
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.REMOTE_FAILURE
        }
    }

    override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
        if (resultData.containsKey(EXTRA_ACTION_ERROR)) {
            Logger.writeLine(
                Log.ERROR,
                "Error executing remote action; Error: %s",
                resultData.getString(EXTRA_ACTION_ERROR)
            )
        }
        if (resultCode == Activity.RESULT_CANCELED && resultData.containsKey(EXTRA_ACTION_DATA)) {
            // Check for remote failure
            val actionData = resultData.getString(EXTRA_ACTION_DATA)
            val action = JSONParser.deserializer(actionData, Action::class.java)
            if (action?.actionStatus == ActionStatus.REMOTE_FAILURE ||
                action?.actionStatus == ActionStatus.REMOTE_PERMISSION_DENIED
            ) {
                scope.launch {
                    sendMessage(
                        null,
                        WearableHelper.ActionsPath,
                        actionData?.stringToBytes()
                    )
                    WearSettingsHelper.launchWearSettings()
                }
            }
        }
    }
}