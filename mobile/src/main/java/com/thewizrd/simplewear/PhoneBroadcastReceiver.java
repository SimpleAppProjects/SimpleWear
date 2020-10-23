package com.thewizrd.simplewear;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.util.Log;

import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.services.TorchService;
import com.thewizrd.simplewear.wearable.WearableWorker;

public class PhoneBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "PhoneBroadcastReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())) {
            WearableWorker.sendStatusUpdate(context);
        } else if (WifiManager.WIFI_STATE_CHANGED_ACTION.equals(intent.getAction())) {
            WearableWorker.sendStatusUpdate(context, WearableWorker.ACTION_SENDWIFIUPDATE,
                    intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_UNKNOWN));
        } else if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(intent.getAction())) {
            WearableWorker.sendStatusUpdate(context, WearableWorker.ACTION_SENDBTUPDATE,
                    intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON));
        } else if (TorchService.ACTION_END_LIGHT.equals(intent.getAction())) {
            TorchService.enqueueWork(context, new Intent(context, TorchService.class)
                    .setAction(TorchService.ACTION_END_LIGHT));
        } else if (TorchService.ACTION_START_LIGHT.equals(intent.getAction())) {
            TorchService.enqueueWork(context, new Intent(context, TorchService.class)
                    .setAction(TorchService.ACTION_START_LIGHT));
        } else {
            Logger.writeLine(Log.INFO, "%s: Unhandled action: %s", TAG, intent.getAction());
        }
    }
}
