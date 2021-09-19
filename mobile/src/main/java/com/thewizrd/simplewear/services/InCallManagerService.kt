/* TODO: Android 12
package com.thewizrd.simplewear.services

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import androidx.lifecycle.MutableLiveData

class InCallManagerService : InCallService() {
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
            value?.let {
                it.registerCallback(callback)
                callState.postValue(it.state)
            }
            field = value
        }

    fun hangup() {
        call?.disconnect()
    }
}
*/