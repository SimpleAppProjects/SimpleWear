package com.thewizrd.simplewear.services

import android.content.Context
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.lifecycle.MutableLiveData

@RequiresApi(Build.VERSION_CODES.S)
class InCallManagerService : InCallService() {
    companion object {
        @RequiresApi(Build.VERSION_CODES.S)
        fun hasPermission(context: Context): Boolean {
            val telecomMgr = context.applicationContext.getSystemService(TelecomManager::class.java)
            return telecomMgr.hasManageOngoingCallsPermission()
        }
    }

    override fun onCallAdded(call: Call?) {
        OngoingCall.call = call
    }

    override fun onCallRemoved(call: Call?) {
        OngoingCall.call = null
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        OngoingCall.callAudioState.postValue(audioState)
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