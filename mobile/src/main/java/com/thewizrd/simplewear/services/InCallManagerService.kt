package com.thewizrd.simplewear.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.CallEndpoint
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.DeprecatedSinceApi
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import com.thewizrd.shared_resources.utils.Logger
import java.util.concurrent.Executors

class InCallManagerService : InCallService() {
    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        fun hasPermission(context: Context): Boolean {
            val telecomMgr = context.applicationContext.getSystemService(TelecomManager::class.java)
            return telecomMgr.hasManageOngoingCallsPermission()
        }
    }

    private var previousAudioRoute: Int? = null
    private var previousCallEndpoint: CallEndpoint? = null
    private val availableCallEndpoints: MutableList<CallEndpoint> = mutableListOf()
    private var isMuted = false

    override fun onCallAdded(call: Call?) {
        OngoingCall.call = call
    }

    override fun onCallRemoved(call: Call?) {
        previousAudioRoute = null
        OngoingCall.call = null
    }

    @Deprecated("Deprecated in Java")
    @DeprecatedSinceApi(api = 34)
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (audioState?.route != CallAudioState.ROUTE_SPEAKER) {
            previousAudioRoute = audioState?.route
        }
        OngoingCall.callAudioState.postValue(audioState)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCallEndpointChanged(callEndpoint: CallEndpoint) {
        if (callEndpoint.endpointType != CallEndpoint.TYPE_SPEAKER) {
            previousAudioRoute = CallAudioState.ROUTE_SPEAKER
            previousCallEndpoint = callEndpoint
        }
        OngoingCall.callAudioState.postValue(createAudioState())
    }

    override fun onMuteStateChanged(isMuted: Boolean) {
        this.isMuted = isMuted
    }

    override fun onAvailableCallEndpointsChanged(availableEndpoints: List<CallEndpoint>) {
        availableCallEndpoints.clear()
        availableCallEndpoints.addAll(availableEndpoints)
    }

    @Suppress("DEPRECATION")
    fun setSpeakerPhoneEnabled(enabled: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (enabled) {
                val speakerEndpoint =
                    availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_SPEAKER }

                if (speakerEndpoint != null) {
                    requestCallEndpointChange(
                        speakerEndpoint,
                        Executors.newSingleThreadExecutor()
                    ) {}
                } else {
                    setAudioRoute(CallAudioState.ROUTE_SPEAKER)
                }
            } else {
                val targetEndpoint = previousCallEndpoint
                    ?: availableCallEndpoints.firstOrNull { it.endpointType == CallEndpoint.TYPE_EARPIECE }

                if (targetEndpoint != null) {
                    requestCallEndpointChange(
                        targetEndpoint,
                        Executors.newSingleThreadExecutor()
                    ) {}
                } else {
                    setAudioRoute(previousAudioRoute ?: CallAudioState.ROUTE_EARPIECE)
                }
            }
        } else {
            setAudioRoute(
                if (enabled) {
                    CallAudioState.ROUTE_SPEAKER
                } else {
                    previousAudioRoute ?: CallAudioState.ROUTE_EARPIECE
                }
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        InCallManagerAdapter.getInstance().setInCallService(this)
        return super.onBind(intent)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        val result = super.onUnbind(intent)

        InCallManagerAdapter.getInstance().clearInCallService()

        return result
    }

    @Suppress("DEPRECATION")
    internal fun createAudioState(): CallAudioState? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            currentCallEndpoint?.let { endpoint ->
                CallAudioState(
                    isMuted,
                    endpoint.getAudioRoute(),
                    availableCallEndpoints.map { it.getAudioRoute() }
                        .reduceOrNull { acc, i -> acc or i } ?: 0
                )
            }
        } else {
            callAudioState
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun CallEndpoint.getAudioRoute(): Int {
        return when (this.endpointType) {
            CallEndpoint.TYPE_BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            CallEndpoint.TYPE_EARPIECE -> CallAudioState.ROUTE_EARPIECE
            CallEndpoint.TYPE_SPEAKER -> CallAudioState.ROUTE_SPEAKER
            CallEndpoint.TYPE_STREAMING -> CallAudioState.ROUTE_STREAMING
            CallEndpoint.TYPE_WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
            else -> -1
        }
    }
}

class InCallManagerAdapter private constructor() {
    private var mInCallService: InCallManagerService? = null

    companion object {
        private const val TAG = "InCallManagerAdapter"

        @SuppressLint("StaticFieldLeak")
        private var instance: InCallManagerAdapter? = null

        @MainThread
        fun getInstance(): InCallManagerAdapter {
            check(Looper.getMainLooper().isCurrentThread)
            if (instance == null) {
                instance = InCallManagerAdapter()
            }
            return instance!!
        }
    }

    fun setInCallService(inCallService: InCallManagerService?) {
        this.mInCallService = inCallService
    }

    fun clearInCallService() {
        mInCallService = null
    }

    fun isInCallServiceAvailable(): Boolean {
        return mInCallService != null
    }

    fun mute(shouldMute: Boolean): Boolean {
        return if (mInCallService != null) {
            mInCallService?.setMuted(shouldMute)
            true
        } else {
            Logger.error(TAG, "mute: mInCallService is null")
            false
        }
    }

    fun setSpeakerPhoneEnabled(enableSpeaker: Boolean): Boolean {
        return if (mInCallService != null) {
            mInCallService?.setSpeakerPhoneEnabled(enableSpeaker)
            true
        } else {
            Logger.error(TAG, "setSpeakerPhoneEnabled: mInCallService is null")
            false
        }
    }

    fun getAudioState(): CallAudioState? {
        return mInCallService?.createAudioState()
    }
}

object OngoingCall {
    val callLiveData = MutableLiveData<Call?>()
    val callState = MutableLiveData<Int>()
    val callAudioState = MutableLiveData<CallAudioState?>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call?, state: Int) {
            super.onStateChanged(call, state)
            callState.postValue(state)
        }
    }

    @Suppress("DEPRECATION")
    var call: Call? = null
        internal set(value) {
            field?.unregisterCallback(callback)
            callLiveData.postValue(value)
            if (value != null) {
                value.registerCallback(callback)
                callState.postValue(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        value.details.state
                    } else {
                        value.state
                    }
                )
            } else {
                callState.postValue(TelephonyManager.CALL_STATE_IDLE)
            }
            field = value
        }

    fun hangup() {
        call?.disconnect()
    }
}