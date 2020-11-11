package com.thewizrd.simplewear.wearable

import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.AppState
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser.deserializer
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToInt
import com.thewizrd.shared_resources.utils.bytesToString
import com.thewizrd.simplewear.MainActivity
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
        runBlocking(Dispatchers.Unconfined) {
            if (messageEvent.path == WearableHelper.StartActivityPath) {
                val startIntent = Intent(this@WearableDataListenerService, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                startActivity(startIntent)
            } else if (messageEvent.path.startsWith(WearableHelper.ActionsPath)) {
                val jsonData: String? = messageEvent.data?.bytesToString()
                val action = deserializer(jsonData, Action::class.java)
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
            } else if (messageEvent.path.startsWith(WearableHelper.MusicPlayersPath)) {
                mWearMgr.sendSupportedMusicPlayers()
            } else if (messageEvent.path == WearableHelper.OpenMusicPlayerPath) {
                val jsonData: String? = messageEvent.data?.bytesToString()
                val pair = deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(messageEvent.sourceNodeId, pkgName, activityName, false)
            } else if (messageEvent.path == WearableHelper.PlayCommandPath) {
                val jsonData: String? = messageEvent.data?.bytesToString()
                val pair = deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.startMusicPlayer(messageEvent.sourceNodeId, pkgName, activityName, true)
            } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
                val deviceName: String? = messageEvent.data?.bytesToString()
                LocalBroadcastManager.getInstance(this@WearableDataListenerService)
                        .sendBroadcast(Intent(ACTION_GETCONNECTEDNODE)
                                .putExtra(EXTRA_NODEDEVICENAME, deviceName))
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerEnabledPath) {
                mWearMgr.sendMessage(messageEvent.sourceNodeId, SleepTimerHelper.SleepTimerEnabledPath,
                        SleepTimerHelper.isSleepTimerInstalled.booleanToBytes())
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStartPath) {
                val timeInMins: Int? = messageEvent.data?.bytesToInt()
                timeInMins?.let { startSleepTimer(it) }
            } else if (messageEvent.path == SleepTimerHelper.SleepTimerStopPath) {
                stopSleepTimer()
            } else if (messageEvent.data != null && messageEvent.path == WearableHelper.AudioStatusPath) {
                mWearMgr.sendAudioModeStatus(messageEvent.sourceNodeId, AudioStreamType.valueOf(messageEvent.data.bytesToString()))
            } else if (messageEvent.path.startsWith(WearableHelper.StatusPath)) {
                mWearMgr.sendStatusUpdate(messageEvent.sourceNodeId, messageEvent.path)
            } else if (messageEvent.path == WearableHelper.AppsPath) {
                mWearMgr.sendApps()
            } else if (messageEvent.path == WearableHelper.LaunchAppPath) {
                val jsonData: String? = messageEvent.data?.bytesToString()
                val pair = deserializer(jsonData, Pair::class.java)
                val pkgName = pair?.first.toString()
                val activityName = pair?.second.toString()
                mWearMgr.launchApp(messageEvent.sourceNodeId, pkgName, activityName)
            }
            return@runBlocking
        }
    }

    private fun startSleepTimer(timeInMins: Int) {
        val startTimerIntent = Intent(SleepTimerHelper.ACTION_START_TIMER)
                .setClassName(SleepTimerHelper.packageName, SleepTimerHelper.PACKAGE_NAME + ".services.TimerService")
                .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MINS, timeInMins)
        ContextCompat.startForegroundService(this, startTimerIntent)
    }

    private fun stopSleepTimer() {
        val stopTimerIntent = Intent(SleepTimerHelper.ACTION_CANCEL_TIMER)
                .setClassName(SleepTimerHelper.packageName, SleepTimerHelper.PACKAGE_NAME + ".services.TimerService")
        ContextCompat.startForegroundService(this, stopTimerIntent)
    }
}