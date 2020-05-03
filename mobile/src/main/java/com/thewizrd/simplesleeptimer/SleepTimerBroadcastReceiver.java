package com.thewizrd.simplesleeptimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

public class SleepTimerBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            if (SleepTimerHelper.ACTION_START_TIMER.equals(intent.getAction())) {
                WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                        .setAction(SleepTimerHelper.ACTION_START_TIMER));
            } else if (SleepTimerHelper.ACTION_CANCEL_TIMER.equals(intent.getAction())) {
                WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                        .setAction(SleepTimerHelper.ACTION_CANCEL_TIMER));
            } else if (SleepTimerHelper.ACTION_TIME_UPDATED.equals(intent.getAction())) {
                WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                        .setAction(SleepTimerHelper.ACTION_TIME_UPDATED)
                        .putExtra(SleepTimerHelper.EXTRA_START_TIME_IN_MS, intent.getLongExtra(SleepTimerHelper.EXTRA_START_TIME_IN_MS, 0))
                        .putExtra(SleepTimerHelper.EXTRA_TIME_IN_MS, intent.getLongExtra(SleepTimerHelper.EXTRA_TIME_IN_MS, 0)));
            }
        }
    }
}
