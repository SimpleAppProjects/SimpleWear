package com.thewizrd.simplewear.wearable

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.stringToBytes
import com.thewizrd.simplewear.CallManagerActivity
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.preferences.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class WearableDataListenerService : WearableListenerService() {
    companion object {
        private const val TAG = "WearableDataListenerService"

        private const val MEDIA_NOT_CHANNEL_ID = "SimpleWear.Wear.mediacontrollerservice"
        private const val CALLS_NOT_CHANNEL_ID = "SimpleWear.Wear.callcontrollerservice"

        private const val MEDIA_LOCUS_ID = "media_ctrlr"
        private const val CALLS_LOCUS_ID = "call_ctrlr"
    }

    @Volatile
    private var mPhoneNodeWithApp: Node? = null

    private lateinit var mNotificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()

        mNotificationManager = getSystemService(NotificationManager::class.java)
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path == WearableHelper.StartActivityPath) {
            val startIntent = Intent(this, PhoneSyncActivity::class.java)
            this.startActivity(startIntent)
        } else if (messageEvent.path == WearableHelper.BtDiscoverPath) {
            this.startActivity(
                Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30)
            )

            GlobalScope.launch(Dispatchers.Default) {
                sendMessage(
                    messageEvent.sourceNodeId,
                    messageEvent.path,
                    Build.MODEL.stringToBytes()
                )
            }
        }
    }

    protected suspend fun sendMessage(nodeID: String, path: String, data: ByteArray?) {
        try {
            Wearable.getMessageClient(this@WearableDataListenerService)
                .sendMessage(nodeID, path, data).await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
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
                } else if (item.uri.path == MediaHelper.MediaPlayerStateBridgePath) {
                    createMediaOngoingActivity(item)
                } else if (item.uri.path == InCallUIHelper.CallStateBridgePath) {
                    createCallOngoingActivity(item)
                }
            }
            if (event.type == DataEvent.TYPE_DELETED) {
                val item = event.dataItem
                if (item.uri.path == MediaHelper.MediaPlayerStateBridgePath) {
                    dismissMediaOngoingActivity()
                } else if (item.uri.path == InCallUIHelper.CallStateBridgePath) {
                    dismissCallOngoingActivity()
                }
            }
        }
    }

    private fun createCallOngoingActivity(item: DataItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initCallControllerNotifChannel()
        }

        val notifTitle = getString(R.string.message_callactive)

        val notifBuilder = NotificationCompat.Builder(this, CALLS_NOT_CHANNEL_ID)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(notifTitle)
            )
            .setContentTitle(notifTitle)
            .setSmallIcon(R.drawable.ic_icon)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_phone_24dp,
                getString(R.string.action_launchcontroller),
                getCallControllerIntent()
            )
            .setLocusId(LocusIdCompat(CALLS_LOCUS_ID))

        val ongoingActivityStatus = Status.Builder()
            .addTemplate(getString(R.string.message_callactive))
            .build()

        val ongoingActivity = OngoingActivity.Builder(applicationContext, 1000, notifBuilder)
            .setStaticIcon(R.drawable.ic_phone_24dp)
            .setTouchIntent(getCallControllerIntent())
            .setStatus(ongoingActivityStatus)
            .setLocusId(LocusIdCompat(CALLS_LOCUS_ID))
            .build()

        ongoingActivity.apply(applicationContext)

        createCallControllerShortcut()
        mNotificationManager.notify(1000, notifBuilder.build())
    }

    private fun createMediaOngoingActivity(item: DataItem) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initMediaControllerNotifChannel()
        }

        val dataMap = DataMapItem.fromDataItem(item).dataMap
        val songTitle = dataMap.getString(MediaHelper.KEY_MEDIA_METADATA_TITLE)
        val notifTitle = getString(R.string.title_nowplaying)

        val notifBuilder = NotificationCompat.Builder(this, MEDIA_NOT_CHANNEL_ID)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(songTitle)
                    .setBigContentTitle(notifTitle)
            )
            .setContentTitle(notifTitle)
            .setContentText(songTitle)
            .setSmallIcon(R.drawable.ic_icon)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_music_note_white_24dp,
                getString(R.string.action_launchcontroller),
                getMediaControllerIntent()
            )
            .setLocusId(LocusIdCompat(MEDIA_LOCUS_ID))

        /*
        val ongoingActivityStatus = Status.Builder()
            .addTemplate(songTitle)
            .build()
         */

        val ongoingActivity = OngoingActivity.Builder(applicationContext, 1001, notifBuilder)
            .setStaticIcon(R.drawable.ic_music_note_white_24dp)
            .setTouchIntent(getMediaControllerIntent())
            //.setStatus(ongoingActivityStatus) // Uses content text from notif
            .setTitle(notifTitle)
            .setLocusId(LocusIdCompat(MEDIA_LOCUS_ID))
            .build()

        ongoingActivity.apply(applicationContext)

        createMediaControllerShortcut()
        mNotificationManager.notify(1001, notifBuilder.build())
    }

    private fun dismissCallOngoingActivity() {
        NotificationManagerCompat.from(this)
            .cancel(1000)
        removeCallControllerShortcut()
    }

    private fun dismissMediaOngoingActivity() {
        NotificationManagerCompat.from(this)
            .cancel(1001)
        removeMediaControllerShortcut()
    }

    private fun getCallControllerIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 1000,
            Intent(this, CallManagerActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getMediaControllerIntent(): PendingIntent {
        return PendingIntent.getActivity(
            this, 1001,
            MediaPlayerActivity.buildAutoLaunchIntent(this),
            PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createMediaControllerShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, MEDIA_LOCUS_ID)
            .setShortLabel(getString(R.string.title_nowplaying))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_play_circle_simpleblue))
            .setIntent(
                MediaPlayerActivity.buildAutoLaunchIntent(this).setAction(Intent.ACTION_VIEW)
            )
            .setLocusId(LocusIdCompat(MEDIA_LOCUS_ID))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    private fun removeMediaControllerShortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(MEDIA_LOCUS_ID))
    }

    private fun createCallControllerShortcut() {
        val shortcut = ShortcutInfoCompat.Builder(this, CALLS_LOCUS_ID)
            .setShortLabel(getString(R.string.title_callcontroller))
            .setIcon(IconCompat.createWithResource(this, R.drawable.ic_phone_simpleblue))
            .setIntent(
                Intent(this, CallManagerActivity::class.java)
                    .setAction(Intent.ACTION_VIEW)
            )
            .setLocusId(LocusIdCompat(CALLS_LOCUS_ID))
            .build()

        ShortcutManagerCompat.pushDynamicShortcut(this, shortcut)
    }

    private fun removeCallControllerShortcut() {
        ShortcutManagerCompat.removeDynamicShortcuts(this, listOf(CALLS_LOCUS_ID))
    }

    override fun onCapabilityChanged(capabilityInfo: CapabilityInfo) {
        mPhoneNodeWithApp = pickBestNodeId(capabilityInfo.nodes)
        if (mPhoneNodeWithApp == null) {
            // Disconnect or dismiss any ongoing activity
            dismissMediaOngoingActivity()
            dismissCallOngoingActivity()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initCallControllerNotifChannel() {
        var channel = mNotificationManager.getNotificationChannel(CALLS_NOT_CHANNEL_ID)
        val notChannelName = getString(R.string.title_callcontroller)
        if (channel == null) {
            channel = NotificationChannel(
                CALLS_NOT_CHANNEL_ID, notChannelName, NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        // Configure channel
        channel.name = notChannelName
        mNotificationManager.createNotificationChannel(channel)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initMediaControllerNotifChannel() {
        var channel = mNotificationManager.getNotificationChannel(MEDIA_NOT_CHANNEL_ID)
        val notChannelName = getString(R.string.title_media_controller)
        if (channel == null) {
            channel = NotificationChannel(
                MEDIA_NOT_CHANNEL_ID, notChannelName, NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        // Configure channel
        channel.name = notChannelName
        mNotificationManager.createNotificationChannel(channel)
    }

    /*
     * There should only ever be one phone in a node set (much less w/ the correct capability), so
     * I am just grabbing the first one (which should be the only one).
    */
    private fun pickBestNodeId(nodes: Collection<Node>): Node? {
        var bestNode: Node? = null

        // Find a nearby node/phone or pick one arbitrarily. Realistically, there is only one phone.
        for (node in nodes) {
            if (node.isNearby) {
                return node
            }
            bestNode = node
        }
        return bestNode
    }
}