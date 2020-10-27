package com.thewizrd.simplewear.controls

import androidx.lifecycle.ViewModel

class SleepTimerViewModel : ViewModel() {
    companion object {
        const val DEFAULT_TIME_MIN = 5
        const val MAX_TIME_IN_MINS = 120
    }

    var progressTimeInMins = DEFAULT_TIME_MIN

    override fun onCleared() {
        super.onCleared()
        progressTimeInMins = DEFAULT_TIME_MIN
    }
}