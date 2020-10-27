package com.thewizrd.simplesleeptimer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.simplewear.wearable.WearableWorker
import java.util.*

class SleepTimerBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when {
            SleepTimerHelper.ACTION_START_TIMER == intent.action -> {
                WearableWorker.enqueueAction(context, SleepTimerHelper.ACTION_START_TIMER)
            }
            SleepTimerHelper.ACTION_CANCEL_TIMER == intent.action -> {
                WearableWorker.enqueueAction(context, SleepTimerHelper.ACTION_CANCEL_TIMER)
            }
            SleepTimerHelper.ACTION_TIME_UPDATED == intent.action -> {
                val map: MutableMap<String, Any> = HashMap()
                map[SleepTimerHelper.EXTRA_START_TIME_IN_MS] = intent.getLongExtra(SleepTimerHelper.EXTRA_START_TIME_IN_MS, 0)
                map[SleepTimerHelper.EXTRA_TIME_IN_MS] = intent.getLongExtra(SleepTimerHelper.EXTRA_TIME_IN_MS, 0)
                WearableWorker.enqueueAction(context, SleepTimerHelper.ACTION_TIME_UPDATED, map)
            }
        }
    }
}