package com.thewizrd.simplewear.sleeptimer

import android.os.Looper
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class SelectedPlayerViewModel : ViewModel() {
    var key = MutableLiveData<String?>()
        private set

    val keyValue: String?
        get() = key.value

    fun setKey(key: String?) {
        if (key != this.key.value) {
            if (Looper.getMainLooper().isCurrentThread) {
                this.key.setValue(key)
            } else {
                this.key.postValue(key)
            }
        }
    }
}