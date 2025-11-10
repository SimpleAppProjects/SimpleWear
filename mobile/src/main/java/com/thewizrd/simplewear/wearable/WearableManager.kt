package com.thewizrd.simplewear.wearable

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.media.MediaBrowserServiceCompat
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityClient.OnCapabilityChangedListener
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.gson.reflect.TypeToken
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
import com.thewizrd.shared_resources.actions.GestureActionState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RemoteActionReceiver
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.TimedAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.actions.ValueAction
import com.thewizrd.shared_resources.actions.ValueActionState
import com.thewizrd.shared_resources.actions.VolumeAction
import com.thewizrd.shared_resources.actions.toRemoteAction
import com.thewizrd.shared_resources.data.AppItemData
import com.thewizrd.shared_resources.data.AppItemSerializer.serialize
import com.thewizrd.shared_resources.helpers.GestureUIHelper
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearSettingsHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.media.MusicPlayersData
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.shared_resources.utils.ImageUtils
import com.thewizrd.shared_resources.utils.ImageUtils.toByteArray
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.shared_resources.wearsettings.PackageValidator
import com.thewizrd.simplewear.helpers.AlarmStateManager
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.helpers.ResolveInfoActivityInfoComparator
import com.thewizrd.simplewear.helpers.dispatchScrollDown
import com.thewizrd.simplewear.helpers.dispatchScrollLeft
import com.thewizrd.simplewear.helpers.dispatchScrollRight
import com.thewizrd.simplewear.helpers.dispatchScrollUp
import com.thewizrd.simplewear.media.MediaAppControllerUtils
import com.thewizrd.simplewear.media.isPlaybackStateActive
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.NotificationListener
import com.thewizrd.simplewear.services.WearAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.nio.ByteBuffer
import java.util.Collections
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class WearableManager(private val mContext: Context) : OnCapabilityChangedListener {
    init {
        init()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var mCapabilityClient: CapabilityClient
    private var mWearNodesWithApp: Collection<Node>? = null

    private var mPackageValidator = PackageValidator(mContext)

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

    suspend fun sendSupportedMusicPlayers(nodeID: String) {
        val appInfos = mutableListOf<ApplicationInfo>()

        mContext.packageManager.queryIntentServices(
            Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE),
            PackageManager.GET_RESOLVED_FILTER
        ).mapTo(appInfos) { it.serviceInfo.applicationInfo }

        val activeSessions = MediaAppControllerUtils.getActiveMediaSessions(
            mContext,
            NotificationListener.getComponentName(mContext)
        )
        MediaAppControllerUtils.getMediaAppsFromControllers(
            mContext,
            activeSessions
        ).run { appInfos.addAll(this) }

        val activeController =
            activeSessions.firstOrNull { it.playbackState?.isPlaybackStateActive() == true }

        // Sort result
        Collections.sort(
            appInfos,
            ApplicationInfo.DisplayNameComparator(mContext.packageManager)
        )

        val supportedPlayers = ArrayList<String>(appInfos.size)
        val musicPlayers = mutableSetOf<AppItemData>()
        var activePlayerKey: String? = null

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
                        val size = mContext.dpToPx(24f).toInt()
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                    } catch (ignored: PackageManager.NameNotFoundException) {
                    }

                    musicPlayers.add(
                        AppItemData(
                            label = label,
                            packageName = appInfo.packageName,
                            activityName = activityInfo.activityInfo.name,
                            iconBitmap = iconBmp?.toByteArray()
                        )
                    )
                    supportedPlayers.add(key)

                    if (activePlayerKey == null && activeController != null && appInfo.packageName == activeController.packageName) {
                        activePlayerKey = key
                    }
                }
            }
        }

        for (info in appInfos) {
            addPlayerInfo(info)
        }

        val playersData = MusicPlayersData(
            musicPlayers = musicPlayers,
            activePlayerKey = activePlayerKey
        )

        try {
            val channelClient = Wearable.getChannelClient(mContext)

            withContext(Dispatchers.IO) {
                val channel =
                    channelClient.openChannel(nodeID, MediaHelper.MusicPlayersPath).await()
                val outputStream = channelClient.getOutputStream(channel).await()
                outputStream.bufferedWriter().use { writer ->
                    writer.write(
                        "data: ${
                            JSONParser.serializer(
                                playersData,
                                MusicPlayersData::class.java
                            )
                        }"
                    )
                    writer.newLine()
                    writer.flush()
                }
                channelClient.close(channel)
            }
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    suspend fun sendApps(nodeID: String) {
        val channelClient = Wearable.getChannelClient(mContext)
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val infos = mContext.packageManager.queryIntentActivities(mainIntent, 0)
        val appItems = ArrayList<AppItemData>(infos.size)

        // Sort result
        infos.sortWith(ResolveInfoActivityInfoComparator(mContext.packageManager))

        val availableApps = ArrayList<String>(infos.size)

        for (info in infos) {
            val key = String.format("%s/%s", info.activityInfo.packageName, info.activityInfo.name)
            if (!availableApps.contains(key)) {
                val label = info.activityInfo.loadLabel(mContext.packageManager)
                var iconBmp: Bitmap? = null

                if (Settings.isLoadAppIcons()) {
                    runCatching {
                        val iconDrwble = info.activityInfo.loadIcon(mContext.packageManager)
                        val size = mContext.dpToPx(24f).toInt()
                        iconBmp = ImageUtils.bitmapFromDrawable(iconDrwble, size, size)
                    }
                }

                appItems.add(
                    AppItemData(
                        label.toString(),
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

            Actions.NFC -> {
                action = ToggleAction(act, PhoneStatusHelper.isNfcEnabled(mContext))
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(action, Action::class.java).stringToBytes()
                )
            }

            else -> {}
        }
    }

    suspend fun sendGestureActionStatus(nodeID: String?) {
        val state = GestureActionState(
            accessibilityEnabled = WearAccessibilityService.isServiceBound(),
            dpadSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
            keyEventSupported = true
        )
        val data = JSONParser.serializer(state, GestureActionState::class.java)
        sendMessage(nodeID, GestureUIHelper.GestureStatusPath, data.stringToBytes())
    }

    suspend fun sendTimedActionsStatus(nodeID: String?) {
        val alarmStateMgr = AlarmStateManager(mContext)
        val actions = alarmStateMgr.getAlarms()
        val data =
            JSONParser.serializer(actions, object : TypeToken<Map<Actions, TimedAction>>() {}.type)
        sendMessage(nodeID, WearableHelper.TimedActionsStatusPath, data.stringToBytes())
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
                var status = PhoneStatusHelper.lockScreen(mContext)
                if ((status == ActionStatus.FAILURE || status == ActionStatus.PERMISSION_DENIED) && WearSettingsHelper.isWearSettingsInstalled()) {
                    status = performRemoteAction(action)

                    if (status == ActionStatus.REMOTE_FAILURE ||
                        status == ActionStatus.REMOTE_PERMISSION_DENIED
                    ) {
                        nA.setActionSuccessful(status)
                    }
                } else {
                    nA.setActionSuccessful(status)
                }
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
                    /**
                     * Starting with Vanilla, calls to change DND state could be ignored if we have no companion associations
                     * If so call companion settings app or else continue as usual
                     */
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !PhoneStatusHelper.companionDeviceAssociated(
                            mContext
                        ) && WearSettingsHelper.isWearSettingsInstalled()
                    ) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            mA.setActionSuccessful(
                                PhoneStatusHelper.setDNDState(
                                    mContext,
                                    DNDChoice.valueOf(mA.choice)
                                )
                            )
                        }
                    } else {
                        mA.setActionSuccessful(
                            PhoneStatusHelper.setDNDState(
                                mContext,
                                DNDChoice.valueOf(mA.choice)
                            )
                        )
                    }
                    sendMessage(nodeID, WearableHelper.ActionsPath, JSONParser.serializer(mA, Action::class.java).stringToBytes())
                } else if (action is ToggleAction) {
                    tA = action
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && !PhoneStatusHelper.companionDeviceAssociated(
                            mContext
                        ) && WearSettingsHelper.isWearSettingsInstalled()
                    ) {
                        val status = performRemoteAction(action)
                        if (status == ActionStatus.REMOTE_FAILURE ||
                            status == ActionStatus.REMOTE_PERMISSION_DENIED
                        ) {
                            tA.setActionSuccessful(
                                PhoneStatusHelper.setDNDState(
                                    mContext,
                                    tA.isEnabled
                                )
                            )
                        }
                    } else {
                        tA.setActionSuccessful(
                            PhoneStatusHelper.setDNDState(
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
                if (WearSettingsHelper.isWearSettingsInstalled()) {
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

            Actions.SLEEPTIMER -> {
                nA = action as NormalAction
                nA.setActionSuccessful(PhoneStatusHelper.sendPauseMusicCommand(mContext))
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(nA, Action::class.java).stringToBytes()
                )
            }

            Actions.TIMEDACTION -> {
                val timedAction = action as TimedAction

                if (timedAction.timeInMillis <= System.currentTimeMillis()) {
                    performAction(nodeID, timedAction.action)
                    timedAction.setActionSuccessful(ActionStatus.SUCCESS)
                } else {
                    timedAction.setActionSuccessful(
                        PhoneStatusHelper.scheduleTimedAction(
                            mContext,
                            timedAction
                        )
                    )
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(timedAction, Action::class.java).stringToBytes()
                )
            }

            Actions.NFC -> {
                tA = action as ToggleAction
                if (WearSettingsHelper.isWearSettingsInstalled()) {
                    val status = performRemoteAction(action)

                    if (status == ActionStatus.REMOTE_FAILURE ||
                        status == ActionStatus.REMOTE_PERMISSION_DENIED
                    ) {
                        tA.setActionSuccessful(
                            PhoneStatusHelper.setNfcEnabled(mContext, tA.isEnabled)
                        )
                    }
                } else {
                    tA.setActionSuccessful(PhoneStatusHelper.setNfcEnabled(mContext, tA.isEnabled))
                }
                sendMessage(
                    nodeID,
                    WearableHelper.ActionsPath,
                    JSONParser.serializer(tA, Action::class.java).stringToBytes()
                )
            }

            else -> {
                Logger.writeLine(
                    Log.WARN,
                    "Unable to perform action. Unsupported - ${action.actionType}"
                )
            }
        }
    }

    suspend fun performScroll(nodeID: String?, scrollData: ByteArray) {
        val buf = ByteBuffer.wrap(scrollData)
        val dX = buf.getFloat()
        val dY = buf.getFloat()
        val width = if (buf.hasRemaining()) buf.getFloat() else null
        val height = if (buf.hasRemaining()) buf.getFloat() else null

        WearAccessibilityService.getInstance()?.let { svc ->
            when {
                dX > 0 -> {
                    svc.dispatchScrollLeft(width?.let { dX / it })
                }

                dX < 0 -> {
                    svc.dispatchScrollRight(width?.let { dX / it })
                }

                dY > 0 -> {
                    svc.dispatchScrollUp(height?.let { dY / it })
                }

                dY < 0 -> {
                    svc.dispatchScrollDown(height?.let { dY / it })
                }
            }
        } ?: run {
            val state = GestureActionState(
                accessibilityEnabled = false,
                dpadSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                keyEventSupported = true
            )
            val data = JSONParser.serializer(state, GestureActionState::class.java)
            sendMessage(nodeID, GestureUIHelper.GestureStatusPath, data.stringToBytes())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun performDPadAction(nodeID: String?, dPadIndex: Int) {
        WearAccessibilityService.getInstance()?.let { svc ->
            when (dPadIndex) {
                0 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_LEFT)
                1 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_UP)
                2 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_RIGHT)
                3 -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_DOWN)
                else -> {}
            }
        } ?: run {
            val state = GestureActionState(
                accessibilityEnabled = false,
                dpadSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                keyEventSupported = true
            )
            val data = JSONParser.serializer(state, GestureActionState::class.java)
            sendMessage(nodeID, GestureUIHelper.GestureStatusPath, data.stringToBytes())
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    suspend fun performDPadClick(nodeID: String?) {
        WearAccessibilityService.getInstance()?.let { svc ->
            svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DPAD_CENTER)
        } ?: run {
            val state = GestureActionState(
                accessibilityEnabled = false,
                dpadSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                keyEventSupported = true
            )
            val data = JSONParser.serializer(state, GestureActionState::class.java)
            sendMessage(nodeID, GestureUIHelper.GestureStatusPath, data.stringToBytes())
        }
    }

    @SuppressLint("GestureBackNavigation")
    suspend fun performKeyEvent(nodeID: String?, key: Int) {
        WearAccessibilityService.getInstance()?.let { svc ->
            when (key) {
                KeyEvent.KEYCODE_BACK -> {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                }

                KeyEvent.KEYCODE_HOME -> {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                }

                KeyEvent.KEYCODE_RECENT_APPS, KeyEvent.KEYCODE_APP_SWITCH -> {
                    svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
                }

                else -> {
                    // TODO: support more events with root?
                }
            }
        } ?: run {
            val state = GestureActionState(
                accessibilityEnabled = false,
                dpadSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU,
                keyEventSupported = true
            )
            val data = JSONParser.serializer(state, GestureActionState::class.java)
            sendMessage(nodeID, GestureUIHelper.GestureStatusPath, data.stringToBytes())
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

    private suspend fun performRemoteAction(action: Action): ActionStatus {
        return try {
            suspendCancellableCoroutine { continuation ->
                val remoteActionReceiver = RemoteActionReceiver().apply {
                    resultReceiver = object : RemoteActionReceiver.IResultReceiver {
                        override fun onReceiveResult(resultCode: Int, resultData: Bundle) {
                            if (resultCode == Activity.RESULT_OK) {
                                if (continuation.isActive) {
                                    continuation.resume(ActionStatus.SUCCESS)
                                }
                            } else {
                                var resultStatus = ActionStatus.REMOTE_FAILURE

                                if (resultData.containsKey(EXTRA_ACTION_ERROR)) {
                                    Logger.writeLine(
                                        Log.ERROR,
                                        "Error executing remote action; Error: %s",
                                        resultData.getString(EXTRA_ACTION_ERROR)
                                    )
                                } else if (resultData.containsKey(EXTRA_ACTION_DATA)) {
                                    val actionData = resultData.getString(EXTRA_ACTION_DATA)
                                    val resultAction =
                                        JSONParser.deserializer(actionData, Action::class.java)
                                    resultStatus = resultAction?.actionStatus ?: resultStatus
                                }

                                if (continuation.isActive) {
                                    continuation.resume(resultStatus)
                                }
                            }
                        }
                    }
                }

                continuation.invokeOnCancellation {
                    remoteActionReceiver.resultReceiver = null
                }

                if (mPackageValidator.isKnownCaller(WearSettingsHelper.getPackageName())) {
                    mContext.startService(
                        Intent(ACTION_PERFORMACTION).apply {
                            component = WearSettingsHelper.getSettingsServiceComponent()
                            putExtra(EXTRA_ACTION_DATA, action.toRemoteAction(remoteActionReceiver))
                            putExtra(EXTRA_ACTION_CALLINGPKG, mContext.packageName)
                        }
                    )
                } else {
                    throw IllegalStateException("Package: ${WearSettingsHelper.getPackageName()} has invalid certificate")
                }
            }
        } catch (ce: CancellationException) {
            ActionStatus.UNKNOWN
        } catch (ise: IllegalStateException) {
            // Likely background service restriction
            Logger.writeLine(Log.ERROR, ise)
            ActionStatus.REMOTE_PERMISSION_DENIED
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
            ActionStatus.REMOTE_FAILURE
        }
    }
}