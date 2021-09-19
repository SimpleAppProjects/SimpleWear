package com.thewizrd.simplewear.services

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.wearable.WearableManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.concurrent.Executors

class CallControllerService : Service(), MessageClient.OnMessageReceivedListener,
    MediaSessionManager.OnActiveSessionsChangedListener {
    private lateinit var mAudioManager: AudioManager
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mMediaSessionManager: MediaSessionManager
    private lateinit var mTelephonyManager: TelephonyManager

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private lateinit var mMainHandler: Handler

    private lateinit var mWearableManager: WearableManager
    private lateinit var mDataClient: DataClient
    private lateinit var mMessageClient: MessageClient

    private var mPhoneStateListener: PhoneStateListener? = null
    private var mTelephonyCallback: TelephonyCallback? = null
    private var mTelecomMediaCtrlr: MediaController? = null

    companion object {
        private const val TAG = "CallControllerService"

        private const val JOB_ID = 1003
        private const val NOT_CHANNEL_ID = "SimpleWear.callcontrollerservice"

        const val ACTION_CONNECTCONTROLLER = "SimpleWear.Droid.action.CONNECT_CONTROLLER"
        const val ACTION_DISCONNECTCONTROLLER = "SimpleWear.Droid.action.DISCONNECT_CONTROLLER"
        private const val ACTION_TOGGLEMUTE = "SimpleWear.Droid.action.TOGGLE_MUTE"
        private const val ACTION_TOGGLESPEAKER = "SimpleWear.Droid.action.TOGGLE_SPEAKER"
        private const val ACTION_HANGUPCALL = "SimpleWear.Droid.action.HANGUP_CALL"
        private const val EXTRA_TOGGLESTATE = "SimpleWear.Droid.extra.TOGGLE_STATE"

        fun enqueueWork(context: Context, work: Intent) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_PHONE_STATE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                ContextCompat.startForegroundService(context, work)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun initChannel() {
        var mChannel = mNotificationManager.getNotificationChannel(NOT_CHANNEL_ID)
        val notChannelName = applicationContext.getString(R.string.not_channel_name_callcontroller)
        if (mChannel == null) {
            mChannel = NotificationChannel(
                NOT_CHANNEL_ID,
                notChannelName,
                NotificationManager.IMPORTANCE_DEFAULT
            )
        }

        // Configure channel
        mChannel.name = notChannelName
        mChannel.setShowBadge(false)
        mChannel.enableLights(false)
        mChannel.enableVibration(false)
        mNotificationManager.createNotificationChannel(mChannel)
    }

    private fun createForegroundNotification(context: Context): Notification {
        val notif = NotificationCompat.Builder(context, NOT_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_settings_phone_24dp)
            setContentTitle(context.getString(R.string.not_title_callcontroller_running))
            setOnlyAlertOnce(true)
            setNotificationSilent()
            priority = NotificationCompat.PRIORITY_DEFAULT
            addAction(
                0,
                context.getString(R.string.action_disconnect),
                PendingIntent.getService(
                    context, 0,
                    Intent(context, CallControllerService::class.java)
                        .setAction(ACTION_DISCONNECTCONTROLLER),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        return notif.build()
    }

    private fun updateNotification(context: Context, callActive: Boolean) {
        val notif = NotificationCompat.Builder(context, NOT_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_settings_phone_24dp)
            setContentTitle(context.getString(if (callActive) R.string.message_callactive else R.string.not_title_callcontroller_running))
            setOnlyAlertOnce(true)
            setNotificationSilent()
            priority =
                if (callActive) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT
            if (callActive) {
                val micMuted = mAudioManager.isMicrophoneMute
                val speakerOn = mAudioManager.isSpeakerphoneOn

                addAction(
                    0,
                    context.getString(if (micMuted) R.string.action_unmute else R.string.action_mute),
                    PendingIntent.getService(
                        context, ACTION_TOGGLEMUTE.hashCode(),
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_TOGGLEMUTE)
                            .putExtra(EXTRA_TOGGLESTATE, !micMuted),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                /*
                addAction(
                    0,
                    context.getString(if (speakerOn) R.string.action_speakerphone_off else R.string.action_speakerphone_on),
                    PendingIntent.getService(
                        context, ACTION_TOGGLESPEAKER.hashCode(),
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_TOGGLESPEAKER)
                            .putExtra(EXTRA_TOGGLESTATE, !speakerOn),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
                */
                addAction(
                    0,
                    context.getString(R.string.action_hangup),
                    PendingIntent.getService(
                        context, ACTION_HANGUPCALL.hashCode(),
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_HANGUPCALL),
                        PendingIntent.FLAG_UPDATE_CURRENT
                    )
                )
            }
            addAction(
                0,
                context.getString(R.string.action_disconnect),
                PendingIntent.getService(
                    context, ACTION_DISCONNECTCONTROLLER.hashCode(),
                    Intent(context, CallControllerService::class.java)
                        .setAction(ACTION_DISCONNECTCONTROLLER),
                    PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
        }

        mNotificationManager.notify(JOB_ID, notif.build())
    }

    override fun onCreate() {
        super.onCreate()

        mMainHandler = Handler(Looper.getMainLooper())
        mAudioManager = getSystemService(AudioManager::class.java)
        mNotificationManager = getSystemService(NotificationManager::class.java)
        mMediaSessionManager = getSystemService(MediaSessionManager::class.java)
        mTelephonyManager = getSystemService(TelephonyManager::class.java)

        mWearableManager = WearableManager(this)
        mDataClient = Wearable.getDataClient(this)
        mMessageClient = Wearable.getMessageClient(this)
        mMessageClient.addListener(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }

        startForeground(JOB_ID, createForegroundNotification(applicationContext))

        registerMediaControllerListener()
        registerPhoneStateListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.writeLine(Log.INFO, "${TAG}: Intent action = ${intent?.action}")

        when (intent?.action) {
            ACTION_CONNECTCONTROLLER -> {
                scope.launch {
                    mTelecomMediaCtrlr = mMediaSessionManager.getActiveSessions(
                        NotificationListener.getComponentName(this@CallControllerService)
                    ).firstOrNull {
                        it.packageName == "com.android.server.telecom"
                    }
                    // Send call state
                    sendCallState(mTelephonyManager.callState, "")
                    mWearableManager.sendMessage(
                        null,
                        InCallUIHelper.MuteMicStatusPath,
                        mAudioManager.isMicrophoneMute.booleanToBytes()
                    )
                    mWearableManager.sendMessage(
                        null,
                        InCallUIHelper.SpeakerphoneStatusPath,
                        mAudioManager.isSpeakerphoneOn.booleanToBytes()
                    )
                }
            }
            ACTION_DISCONNECTCONTROLLER -> {
                scope.launch {
                    // Delay in case service was just started as foreground
                    delay(1000)
                    stopSelf()
                }
            }
            ACTION_HANGUPCALL -> {
                sendHangupEvent()
            }
            ACTION_TOGGLEMUTE -> {
                if (intent.hasExtra(EXTRA_TOGGLESTATE)) {
                    val toggle = intent.getBooleanExtra(EXTRA_TOGGLESTATE, false)
                    toggleMicMute(mute = toggle)
                }
            }
            /*
            ACTION_TOGGLESPEAKER -> {
                if (intent.hasExtra(EXTRA_TOGGLESTATE)) {
                    val toggle = intent.getBooleanExtra(EXTRA_TOGGLESTATE, false)
                    toggleSpeakerphone(on = toggle)
                }
            }
            */
        }

        return START_STICKY
    }

    override fun onDestroy() {
        removeCallState()

        unregisterPhoneStateListener()
        unregisterMediaControllerListener()
        mMessageClient.removeListener(this)
        mWearableManager.unregister()

        stopForeground(true)
        scope.cancel()
        super.onDestroy()
    }

    private fun removeCallState() {
        scope.launch {
            Log.d(TAG, "removeCallState")
            mDataClient.deleteDataItems(
                WearableHelper.getWearDataUri(InCallUIHelper.CallStatePath)
            ).await()
        }
    }

    private fun registerPhoneStateListener() {
        mTelephonyManager?.let { tm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mTelephonyCallback =
                    object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                        override fun onCallStateChanged(state: Int) {
                            this@CallControllerService.onCallStateChanged(state, "")
                        }
                    }
                tm.registerTelephonyCallback(
                    Executors.newSingleThreadExecutor(),
                    mTelephonyCallback!!
                )
            } else {
                mPhoneStateListener = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    object : PhoneStateListener(Executors.newSingleThreadExecutor()) {
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            super.onCallStateChanged(state, phoneNumber)
                            this@CallControllerService.onCallStateChanged(state, phoneNumber)
                        }
                    }
                } else {
                    object : PhoneStateListener() {
                        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                            super.onCallStateChanged(state, phoneNumber)
                            this@CallControllerService.onCallStateChanged(state, phoneNumber)
                        }
                    }
                }
                tm.listen(mPhoneStateListener!!, PhoneStateListener.LISTEN_CALL_STATE)
            }
        }
    }

    private fun unregisterPhoneStateListener() {
        mTelephonyManager?.let { tm ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mTelephonyCallback?.let {
                    tm.unregisterTelephonyCallback(it)
                }
            } else {
                mPhoneStateListener?.let {
                    tm.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
    }

    private fun registerMediaControllerListener() {
        mMediaSessionManager.addOnActiveSessionsChangedListener(
            this,
            NotificationListener.getComponentName(this)
        )
    }

    private fun unregisterMediaControllerListener() {
        mMediaSessionManager.removeOnActiveSessionsChangedListener(this)
    }

    private fun onCallStateChanged(newState: Int, phoneNo: String?) {
        when (newState) {
            TelephonyManager.CALL_STATE_IDLE -> {
                // No call active
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Call is active; no other calls or ringing or waiting
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                // A new call is ringing or one call is active and new call is waiting
            }
        }
        scope.launch {
            sendCallState(newState, phoneNo)
        }
        updateNotification(this, newState != TelephonyManager.CALL_STATE_IDLE)
    }

    private suspend fun sendCallState(state: Int? = null, phoneNo: String? = null) {
        val mapRequest = PutDataMapRequest.create(InCallUIHelper.CallStatePath)

        mapRequest.dataMap.putString(
            InCallUIHelper.KEY_CALLERNAME, /*OngoingCall.call?.details?.callerDisplayName ?: */
            phoneNo ?: ""
        )

        /*
        val ongoingCallActive = OngoingCall.call?.let {
            it.state != TelephonyManager.CALL_STATE_IDLE
        } ?: false
        */
        val callActive = state?.let { it != TelephonyManager.CALL_STATE_IDLE } ?: false
        mapRequest.dataMap.putBoolean(
            InCallUIHelper.KEY_CALLACTIVE, /*ongoingCallActive || */
            callActive
        )

        mapRequest.setUrgent()
        try {
            mDataClient.putDataItem(mapRequest.asPutDataRequest())
                .await()
        } catch (e: Exception) {
            Logger.writeLine(Log.ERROR, e)
        }
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            InCallUIHelper.EndCallPath -> {
                sendHangupEvent()
            }
            InCallUIHelper.MuteMicPath -> {
                val toggle = messageEvent.data.bytesToBool()
                toggleMicMute(messageEvent.sourceNodeId, toggle)
            }
            InCallUIHelper.MuteMicStatusPath -> {
                scope.launch {
                    mWearableManager.sendMessage(
                        messageEvent.sourceNodeId,
                        InCallUIHelper.MuteMicStatusPath,
                        mAudioManager.isMicrophoneMute.booleanToBytes()
                    )
                }
            }
            /*
            InCallUIHelper.SpeakerphonePath -> {
                val toggle = messageEvent.data.bytesToBool()
                toggleSpeakerphone(messageEvent.sourceNodeId, toggle)
            }
            InCallUIHelper.SpeakerphoneStatusPath -> {
                scope.launch {
                    mWearableManager.sendMessage(messageEvent.sourceNodeId, InCallUIHelper.SpeakerphoneStatusPath, mAudioManager.isSpeakerphoneOn.booleanToBytes())
                }
            }*/
        }
    }

    private fun sendHangupEvent() {
        mTelecomMediaCtrlr?.dispatchMediaButtonEvent(
            KeyEvent(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_HEADSETHOOK
            )
        )
        mTelecomMediaCtrlr?.dispatchMediaButtonEvent(
            KeyEvent(
                KeyEvent.ACTION_UP,
                KeyEvent.KEYCODE_HEADSETHOOK
            )
        )
    }

    private fun toggleMicMute(nodeID: String? = null, mute: Boolean) {
        PhoneStatusHelper.muteMicrophone(this, mute)
        scope.launch {
            mWearableManager.sendMessage(
                nodeID,
                InCallUIHelper.MuteMicStatusPath,
                mAudioManager.isMicrophoneMute.booleanToBytes()
            )
        }
        updateNotification(this, mTelephonyManager.callState != TelephonyManager.CALL_STATE_IDLE)
    }

    /*
    private fun toggleSpeakerphone(nodeID: String? = null, on: Boolean) {
        Log.d("AudioMode", "mode = " + mAudioManager.mode)
        PhoneStatusHelper.setSpeakerphoneOn(this, on)
        scope.launch {
            mWearableManager.sendMessage(nodeID, InCallUIHelper.SpeakerphoneStatusPath, mAudioManager.isSpeakerphoneOn.booleanToBytes())
        }
        updateNotification(this, mTelephonyManager.callState != TelephonyManager.CALL_STATE_IDLE)
    }*/

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        mTelecomMediaCtrlr = controllers?.firstOrNull {
            it.packageName == "com.android.server.telecom"
        }
    }
}