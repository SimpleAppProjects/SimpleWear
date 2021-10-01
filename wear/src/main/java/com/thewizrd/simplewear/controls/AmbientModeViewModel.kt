package com.thewizrd.simplewear.controls

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class AmbientModeViewModel : ViewModel() {
    val ambientModeEnabled = MutableLiveData(false)
    val isLowBitAmbient = MutableLiveData(false)
    val doBurnInProtection = MutableLiveData(false)

    override fun onCleared() {
        super.onCleared()
    }
}