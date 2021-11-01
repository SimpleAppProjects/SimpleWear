package com.thewizrd.simplewear.services

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.MainThread
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData
import timber.log.Timber

@RequiresApi(Build.VERSION_CODES.S)
class InCallManagerService : InCallService() {
    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        fun hasPermission(context: Context): Boolean {
            val telecomMgr = context.applicationContext.getSystemService(TelecomManager::class.java)
            return telecomMgr.hasManageOngoingCallsPermission()
        }
    }

    private var previousAudioRoute: Int? = null

    override fun onCallAdded(call: Call?) {
        OngoingCall.call = call
    }

    override fun onCallRemoved(call: Call?) {
        previousAudioRoute = null
        OngoingCall.call = null
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        if (audioState?.route != CallAudioState.ROUTE_SPEAKER) {
            previousAudioRoute = audioState?.route
        }
        OngoingCall.callAudioState.postValue(audioState)
    }

    fun setSpeakerPhoneEnabled(enabled: Boolean) {
        setAudioRoute(
            if (enabled) {
                CallAudioState.ROUTE_SPEAKER
            } else {
                previousAudioRoute ?: CallAudioState.ROUTE_EARPIECE
            }
        )
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
}

@RequiresApi(Build.VERSION_CODES.S)
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
            Timber.tag(TAG).e("mute: mInCallService is null")
            false
        }
    }

    fun setSpeakerPhoneEnabled(enableSpeaker: Boolean): Boolean {
        return if (mInCallService != null) {
            mInCallService?.setSpeakerPhoneEnabled(enableSpeaker)
            true
        } else {
            Timber.tag(TAG).e("setSpeakerPhoneEnabled: mInCallService is null")
            false
        }
    }

    fun getAudioState(): CallAudioState? {
        return mInCallService?.callAudioState
    }
}

@RequiresApi(Build.VERSION_CODES.S)
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