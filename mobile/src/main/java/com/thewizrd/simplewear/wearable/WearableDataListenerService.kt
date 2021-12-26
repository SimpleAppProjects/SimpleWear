package com.thewizrd.simplewear.wearable

import android.content.Intent
import android.os.Build
import androidx.core.util.Pair
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.*
import com.thewizrd.simplewear.MainActivity
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.media.MediaAppControllerUtils
import com.thewizrd.simplewear.media.MediaControllerService
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.services.CallControllerService
import com.thewizrd.simplewear.services.NotificationListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableDataListenerService"
        const val ACTION_GETCONNECTEDNODE = "SimpleWear.Droid.action.GET_CONNECTED_NODE"
        const val EXTRA_NODEDEVICENAME = "SimpleWear.Droid.extra.NODE_DEVICE_NAME"
        private const val JOB_ID = 1000
    }

    private lateinit var mWearMgr: WearableManager

    override fun onCreate() {
        super.onCreate()
        mWearMgr = WearableManager(this)
    }

    override fun onDestroy() {
        mWearMgr.unregister()
        super.onDestroy()
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        runBlocking(Dispatchers.Default) {
            val ctx = this@WearableDataListenerService

            if (messageEvent.path == WearableHelper.StartActivityPath) {
                val startIntent = Intent(ctx, MainActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(startIntent)
            } else if (messageEvent.path.startsWith(WearableHelper.ActionsPath)) {
                val jsonData = messageEvent.data.bytesToString()
                val action = JSONParser.deserializer(jsonData, Action::class.java)
                mWearMgr.performAction(messageEvent.sourceNodeId, action!!)
            } else if (messageEvent.path.startsWith(WearableHelper.UpdatePath)) {
                mWearMgr.sendStatusUpdate(messageEvent.sourceNodeId, null)
                mWearMgr.sendActionsUpdate(messageEvent.sourceNodeId)
            } else if (messageEvent.path == WearableHelper.AppStatePath) {
                val wearAppState = AppState.valueOf(messageEvent.data.bytesToString())
                if (wearAppState == AppState.FOREGROUND) {
                    mWearMgr.sendStatusUpdate(messageEvent.sourceNodeId, null)
                    mWearMgr.sendActionsUpdate(messageEvent.sourceNodeId)
                }
            } else if (messageEvent.path.startsWith(MediaHelper.MusicPlayersPath)) {
                if (NotificationListener.isEnabled(ctx)) {
                    mWearMgr.sendSupportedMusicPlayers()
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.SUCCESS.name.stringToBytes()
                    )
                } else {
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                }
            } else if (messageEvent.path == MediaHelper.OpenMusicPlayerPath) {
                val jsonData = messageEvent.data.bytesToString()
                val pair = JSONParser.deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(messageEvent.sourceNodeId, pkgName, activityName, false)
            } else if (messageEvent.path == MediaHelper.PlayCommandPath) {
                val jsonData = messageEvent.data.bytesToString()
                val pair = JSONParser.deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(messageEvent.sourceNodeId, pkgName, activityName, true)
            } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
                val deviceName = messageEvent.data.bytesToString()
                LocalBroadcastManager.getInstance(ctx)
                    .sendBroadcast(
                        Intent(ACTION_GETCONNECTEDNODE)
                            .putExtra(EXTRA_NODEDEVICENAME, deviceName)
                    )
            } else if (messageEvent.data != null && messageEvent.path == WearableHelper.AudioStatusPath) {
                mWearMgr.sendAudioModeStatus(
                    messageEvent.sourceNodeId,
                    AudioStreamType.valueOf(messageEvent.data.bytesToString())
                )
            } else if (messageEvent.path == WearableHelper.AudioVolumePath) {
                val jsonData = messageEvent.data.bytesToString()
                val streamData = JSONParser.deserializer(jsonData, AudioStreamState::class.java)
                streamData?.let {
                    mWearMgr.setStreamVolume(messageEvent.sourceNodeId, it)
                }
            } else if (messageEvent.path == WearableHelper.ValueStatusPath) {
                val actionType = Actions.valueOf(messageEvent.data.bytesToInt())
                mWearMgr.sendValueStatus(messageEvent.sourceNodeId, actionType)
            } else if (messageEvent.path == WearableHelper.ValueStatusSetPath) {
                val jsonData = messageEvent.data.bytesToString()
                val valueData = JSONParser.deserializer(jsonData, ValueActionState::class.java)
                valueData?.let {
                    mWearMgr.setActionValue(messageEvent.sourceNodeId, it)
                }
            } else if (messageEvent.path == WearableHelper.BrightnessModePath) {
                mWearMgr.toggleBrightnessMode(messageEvent.sourceNodeId)
            } else if (messageEvent.path.startsWith(WearableHelper.StatusPath)) {
                mWearMgr.sendStatusUpdate(messageEvent.sourceNodeId, messageEvent.path)
            } else if (messageEvent.path == WearableHelper.AppsPath) {
                mWearMgr.sendApps(messageEvent.sourceNodeId)
            } else if (messageEvent.path == WearableHelper.LaunchAppPath) {
                val jsonData = messageEvent.data.bytesToString()
                val pair = JSONParser.deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.launchApp(messageEvent.sourceNodeId, pkgName, activityName)
            } else if (messageEvent.path == MediaHelper.MediaPlayerConnectPath) {
                if (NotificationListener.isEnabled(ctx)) {
                    val isAutoLaunch =
                        messageEvent.data.size == 1 && messageEvent.data.bytesToBool()
                    val packageName = messageEvent.data.bytesToString()

                    MediaControllerService.enqueueWork(
                        ctx, Intent(ctx, MediaControllerService::class.java)
                            .setAction(MediaControllerService.ACTION_CONNECTCONTROLLER).apply {
                                if (isAutoLaunch) {
                                    putExtra(MediaControllerService.EXTRA_AUTOLAUNCH, true)
                                } else {
                                    putExtra(MediaControllerService.EXTRA_PACKAGENAME, packageName)
                                }
                            }
                    )

                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.SUCCESS.name.stringToBytes()
                    )
                } else {
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                }
            } else if (messageEvent.path == MediaHelper.MediaPlayerDisconnectPath) {
                MediaControllerService.enqueueWork(
                    ctx, Intent(ctx, MediaControllerService::class.java)
                        .setAction(MediaControllerService.ACTION_DISCONNECTCONTROLLER)
                        .putExtra(
                            MediaControllerService.EXTRA_FORCEDISCONNECT,
                            !Settings.isBridgeMediaEnabled()
                        )
                )
            } else if (messageEvent.path == MediaHelper.MediaPlayerAutoLaunchPath) {
                if (NotificationListener.isEnabled(ctx)) {
                    val status = PhoneStatusHelper.isMusicActive(ctx, false)

                    if (status == ActionStatus.SUCCESS || MediaAppControllerUtils.isMediaActive(
                            ctx,
                            NotificationListener.getComponentName(ctx)
                        )
                    ) {
                        MediaControllerService.enqueueWork(
                            ctx, Intent(ctx, MediaControllerService::class.java)
                                .setAction(MediaControllerService.ACTION_CONNECTCONTROLLER)
                                .putExtra(MediaControllerService.EXTRA_AUTOLAUNCH, true)
                        )

                        mWearMgr.sendMessage(
                            messageEvent.sourceNodeId,
                            MediaHelper.MediaPlayerAutoLaunchPath,
                            ActionStatus.SUCCESS.name.stringToBytes()
                        )
                    }
                } else {
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId,
                        MediaHelper.MediaPlayerAutoLaunchPath,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                }
            }
            /* InCall Actions */
            else if (messageEvent.path == InCallUIHelper.CallStatePath) {
                if (PhoneStatusHelper.callStatePermissionEnabled(ctx) &&
                    (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                            PhoneStatusHelper.companionDeviceAssociated(ctx))
                ) {
                    CallControllerService.enqueueWork(
                        ctx, Intent(ctx, CallControllerService::class.java)
                            .setAction(CallControllerService.ACTION_CONNECTCONTROLLER)
                    )

                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.SUCCESS.name.stringToBytes()
                    )
                } else {
                    mWearMgr.sendMessage(
                        messageEvent.sourceNodeId, messageEvent.path,
                        ActionStatus.PERMISSION_DENIED.name.stringToBytes()
                    )
                }
            } else if (messageEvent.path == InCallUIHelper.DisconnectPath) {
                CallControllerService.enqueueWork(
                    ctx, Intent(ctx, CallControllerService::class.java)
                        .setAction(CallControllerService.ACTION_DISCONNECTCONTROLLER)
                        .putExtra(
                            CallControllerService.EXTRA_FORCEDISCONNECT,
                            !Settings.isBridgeCallsEnabled()
                        )
                )
            }
            return@runBlocking
        }
    }

    override fun onDataChanged(dataEventBuffer: DataEventBuffer) {
        super.onDataChanged(dataEventBuffer)

        for (event in dataEventBuffer) {
            if (event.type == DataEvent.TYPE_CHANGED) {
                val item = event.dataItem
                if (item.uri.path == WearableHelper.AppsIconSettingsPath) {
                    runCatching {
                        val dataMap = DataMapItem.fromDataItem(item).dataMap
                        if (dataMap.containsKey(WearableHelper.KEY_ICON)) {
                            val loadIcons = dataMap.getBoolean(WearableHelper.KEY_ICON)
                            Settings.setLoadAppIcons(loadIcons)
                        }
                    }
                    break
                }
            }
        }
    }
}