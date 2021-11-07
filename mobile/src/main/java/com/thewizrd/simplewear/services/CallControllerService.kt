package com.thewizrd.simplewear.services

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.CallAudioState
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.android.gms.wearable.*
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.toImmutableCompatFlag
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.booleanToBytes
import com.thewizrd.shared_resources.utils.bytesToBool
import com.thewizrd.shared_resources.utils.bytesToChar
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.wearable.WearableManager
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import timber.log.Timber
import java.util.concurrent.Executors

class CallControllerService : LifecycleService(), MessageClient.OnMessageReceivedListener,
    MediaSessionManager.OnActiveSessionsChangedListener {
    private lateinit var mAudioManager: AudioManager
    private lateinit var mNotificationManager: NotificationManager
    private lateinit var mMediaSessionManager: MediaSessionManager
    private lateinit var mTelephonyManager: TelephonyManager
    private lateinit var mTelecomManager: TelecomManager

    private var mForegroundNotification: Notification? = null

    private val scope = CoroutineScope(
        SupervisorJob() + Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    )
    private var disconnectJob: Job? = null
    private lateinit var mMainHandler: Handler

    private lateinit var mWearableManager: WearableManager
    private lateinit var mDataClient: DataClient
    private lateinit var mMessageClient: MessageClient

    private var mPhoneStateListener: PhoneStateListener? = null
    private var mTelephonyCallback: TelephonyCallback? = null
    private var mTelecomMediaCtrlr: MediaController? = null
    private lateinit var mInCallManagerAdapter: InCallManagerAdapter

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

        const val EXTRA_FORCEDISCONNECT = "SimpleWear.Droid.extra.FORCE_DISCONNECT"

        fun enqueueWork(context: Context, work: Intent) {
            if (hasPermissions(context)) {
                ContextCompat.startForegroundService(context, work)
            }
        }

        fun hasPermissions(context: Context): Boolean {
            return PhoneStatusHelper.callStatePermissionEnabled(context) &&
                    NotificationListener.isEnabled(context)
        }
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

    private fun getForegroundNotification(): Notification {
        if (mForegroundNotification == null) {
            val context = applicationContext

            mForegroundNotification = NotificationCompat.Builder(context, NOT_CHANNEL_ID).apply {
                setSmallIcon(R.drawable.ic_settings_phone_24dp)
                setContentTitle(context.getString(R.string.not_title_callcontroller_running))
                setOnlyAlertOnce(true)
                setSilent(true)
                priority = NotificationCompat.PRIORITY_DEFAULT
                addAction(
                    0,
                    context.getString(R.string.action_disconnect),
                    PendingIntent.getService(
                        context, 0,
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_DISCONNECTCONTROLLER),
                        PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
                    )
                )
            }.build()
        }

        return mForegroundNotification!!
    }

    private fun updateNotification(context: Context, callActive: Boolean) {
        val notif = NotificationCompat.Builder(context, NOT_CHANNEL_ID).apply {
            setSmallIcon(R.drawable.ic_settings_phone_24dp)
            setContentTitle(context.getString(if (callActive) R.string.message_callactive else R.string.not_title_callcontroller_running))
            setOnlyAlertOnce(true)
            setSilent(true)
            priority =
                if (callActive) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT
            if (callActive) {
                val micMuted = isMicrophoneMute()
                val speakerOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    isSpeakerPhoneEnabled()
                } else {
                    false
                }

                addAction(
                    0,
                    context.getString(if (micMuted) R.string.action_unmute else R.string.action_mute),
                    PendingIntent.getService(
                        context, ACTION_TOGGLEMUTE.hashCode(),
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_TOGGLEMUTE)
                            .putExtra(EXTRA_TOGGLESTATE, !micMuted),
                        PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
                    )
                )
                if (supportsSpeakerToggle()) {
                    addAction(
                        0,
                        context.getString(if (speakerOn) R.string.action_speakerphone_off else R.string.action_speakerphone_on),
                        PendingIntent.getService(
                            context, ACTION_TOGGLESPEAKER.hashCode(),
                            Intent(context, CallControllerService::class.java)
                                .setAction(ACTION_TOGGLESPEAKER)
                                .putExtra(EXTRA_TOGGLESTATE, !speakerOn),
                            PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
                        )
                    )
                }
                addAction(
                    0,
                    context.getString(R.string.action_hangup),
                    PendingIntent.getService(
                        context, ACTION_HANGUPCALL.hashCode(),
                        Intent(context, CallControllerService::class.java)
                            .setAction(ACTION_HANGUPCALL),
                        PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
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
                    PendingIntent.FLAG_UPDATE_CURRENT.toImmutableCompatFlag()
                )
            )
        }

        mForegroundNotification = notif.build()
        mNotificationManager.notify(JOB_ID, mForegroundNotification!!)
    }

    override fun onCreate() {
        super.onCreate()

        mMainHandler = Handler(Looper.getMainLooper())
        mAudioManager = getSystemService(AudioManager::class.java)
        mNotificationManager = getSystemService(NotificationManager::class.java)
        mMediaSessionManager = getSystemService(MediaSessionManager::class.java)
        mTelephonyManager = getSystemService(TelephonyManager::class.java)
        mTelecomManager = getSystemService(TelecomManager::class.java)

        mWearableManager = WearableManager(this)
        mDataClient = Wearable.getDataClient(this)
        mMessageClient = Wearable.getMessageClient(this)
        mMessageClient.addListener(this)

        mInCallManagerAdapter = InCallManagerAdapter.getInstance()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            initChannel()
        }

        startForeground(JOB_ID, getForegroundNotification())

        registerMediaControllerListener()
        registerPhoneStateListener()
        OngoingCall.callState.observe(this, {
            scope.launch {
                onCallStateChanged(it)
            }
        })
        OngoingCall.callAudioState.observe(this, {
            scope.launch {
                it?.let {
                    mWearableManager.sendMessage(
                        null,
                        InCallUIHelper.MuteMicStatusPath,
                        it.isMuted.booleanToBytes()
                    )
                    mWearableManager.sendMessage(
                        null,
                        InCallUIHelper.SpeakerphoneStatusPath,
                        (it.route == CallAudioState.ROUTE_SPEAKER).booleanToBytes()
                    )
                }
            }
        })
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        disconnectJob?.cancel()
        startForeground(JOB_ID, getForegroundNotification())

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
                        isMicrophoneMute().booleanToBytes()
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        mWearableManager.sendMessage(
                            null,
                            InCallUIHelper.SpeakerphoneStatusPath,
                            isSpeakerPhoneEnabled().booleanToBytes()
                        )
                    }
                }
            }
            ACTION_DISCONNECTCONTROLLER -> {
                val disconnect = intent.getBooleanExtra(EXTRA_FORCEDISCONNECT, true)
                if (disconnect) {
                    disconnectJob = scope.launch {
                        // Delay in case service was just started as foreground
                        delay(1500)
                        stopSelf()
                    }
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
            ACTION_TOGGLESPEAKER -> {
                if (intent.hasExtra(EXTRA_TOGGLESTATE)) {
                    val toggle = intent.getBooleanExtra(EXTRA_TOGGLESTATE, false)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        toggleSpeakerphone(on = toggle)
                    }
                }
            }
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
            Timber.tag(TAG).d("removeCallState")
            runCatching {
                mDataClient.deleteDataItems(
                    WearableHelper.getWearDataUri(InCallUIHelper.CallStatePath)
                ).await()
            }.onFailure {
                Logger.writeLine(Log.ERROR, it)
            }
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

    private fun onCallStateChanged(newState: Int, phoneNo: String? = null) {
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
            InCallUIHelper.KEY_CALLERNAME,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                OngoingCall.call?.details?.contactDisplayName
            } else {
                null
            } ?: OngoingCall.call?.details?.callerDisplayName ?: phoneNo ?: ""
        )

        val callState = state ?: OngoingCall.call?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                it.details.state
            } else {
                it.state
            }
        } ?: TelephonyManager.CALL_STATE_IDLE

        val callActive = callState != TelephonyManager.CALL_STATE_IDLE
        mapRequest.dataMap.putBoolean(InCallUIHelper.KEY_CALLACTIVE, callActive)

        var supportedFeatures = 0
        if (supportsSpeakerToggle()) {
            supportedFeatures += InCallUIHelper.INCALL_FEATURE_SPEAKERPHONE
        }
        if (OngoingCall.call != null) {
            supportedFeatures += InCallUIHelper.INCALL_FEATURE_DTMF
        }

        mapRequest.dataMap.putInt(InCallUIHelper.KEY_SUPPORTEDFEATURES, supportedFeatures)

        mapRequest.setUrgent()
        try {
            mDataClient.deleteDataItems(mapRequest.uri).await()
            mDataClient.putDataItem(mapRequest.asPutDataRequest())
                .await()
            if (callActive) {
                if (Settings.isBridgeCallsEnabled()) {
                    mDataClient.putDataItem(
                        PutDataRequest.create(InCallUIHelper.CallStateBridgePath).setUrgent()
                    ).await()
                }
            } else {
                mDataClient.deleteDataItems(WearableHelper.getWearDataUri(InCallUIHelper.CallStateBridgePath))
                    .await()
            }
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
                        isMicrophoneMute().booleanToBytes()
                    )
                }
            }
            InCallUIHelper.SpeakerphonePath -> {
                val toggle = messageEvent.data.bytesToBool()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    toggleSpeakerphone(messageEvent.sourceNodeId, toggle)
                }
            }
            InCallUIHelper.SpeakerphoneStatusPath -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    scope.launch {
                        mWearableManager.sendMessage(
                            messageEvent.sourceNodeId,
                            InCallUIHelper.SpeakerphoneStatusPath,
                            isSpeakerPhoneEnabled().booleanToBytes()
                        )
                    }
                }
            }
            InCallUIHelper.DTMFPath -> {
                when (val char = messageEvent.data.bytesToChar()) {
                    '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '#' -> {
                        scope.launch {
                            OngoingCall.call?.run {
                                playDtmfTone(char)
                                stopDtmfTone()
                            }
                        }
                    }
                    else -> {
                        // no-op
                    }
                }
            }
        }
    }

    private fun sendHangupEvent() {
        OngoingCall.call?.disconnect() ?: run {
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
    }

    private fun toggleMicMute(nodeID: String? = null, mute: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mInCallManagerAdapter.isInCallServiceAvailable()) {
            mInCallManagerAdapter.mute(mute)
        } else {
            PhoneStatusHelper.muteMicrophone(this, mute)
        }
        scope.launch {
            mWearableManager.sendMessage(
                nodeID,
                InCallUIHelper.MuteMicStatusPath,
                isMicrophoneMute().booleanToBytes()
            )
        }
        updateNotification(this, isInCall())
    }

    private fun isMicrophoneMute(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mInCallManagerAdapter.isInCallServiceAvailable()) {
            mInCallManagerAdapter.getAudioState()?.isMuted ?: false
        } else {
            mAudioManager.isMicrophoneMute
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun toggleSpeakerphone(nodeID: String? = null, on: Boolean) {
        mInCallManagerAdapter.setSpeakerPhoneEnabled(on)
        scope.launch {
            mWearableManager.sendMessage(
                nodeID,
                InCallUIHelper.SpeakerphoneStatusPath,
                isSpeakerPhoneEnabled().booleanToBytes()
            )
        }
        updateNotification(this, isInCall())
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun isSpeakerPhoneEnabled(): Boolean {
        return mInCallManagerAdapter.getAudioState()?.route == CallAudioState.ROUTE_SPEAKER
    }

    override fun onActiveSessionsChanged(controllers: MutableList<MediaController>?) {
        mTelecomMediaCtrlr = controllers?.firstOrNull {
            it.packageName == "com.android.server.telecom"
        }
    }

    @SuppressLint("MissingPermission")
    private fun isInCall(): Boolean {
        return runCatching {
            mTelecomManager.isInCall
        }.getOrDefault(mTelephonyManager.callState != TelephonyManager.CALL_STATE_IDLE)
    }

    @SuppressLint("MissingPermission")
    private fun supportsSpeakerToggle(): Boolean {
        return runCatching {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && mInCallManagerAdapter.isInCallServiceAvailable()
        }.getOrDefault(false)
    }
}