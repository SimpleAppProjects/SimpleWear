package com.thewizrd.simplewear;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.wearable.WearableDataListenerService;

public class PhoneBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "PhoneBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                    .setAction(WearableDataListenerService.ACTION_SENDSTATUSUPDATE));
        } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                    .setAction(WearableDataListenerService.ACTION_SENDWIFIUPDATE)
                    .putExtra(WearableDataListenerService.EXTRA_STATUS,
                            intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN)));
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            WearableDataListenerService.enqueueWork(context, new Intent(context, WearableDataListenerService.class)
                    .setAction(WearableDataListenerService.ACTION_SENDBTUPDATE)
                    .putExtra(WearableDataListenerService.EXTRA_STATUS,
                            intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON)));
        } else {
            Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.getAction());
        }
    }
}
