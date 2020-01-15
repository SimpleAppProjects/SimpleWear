package com.thewizrd.simplewear.wearable;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;
import com.thewizrd.shared_resources.helpers.WearableHelper;
import com.thewizrd.shared_resources.utils.Logger;
import com.thewizrd.simplewear.LaunchActivity;

import static com.thewizrd.shared_resources.utils.SerializationUtils.stringToBytes;

public class WearableDataListenerService extends WearableListenerService {
    private static final String TAG = "WearableDataListenerService";

    @Override
    public void onMessageReceived(final MessageEvent messageEvent) {
        if (messageEvent.getPath().equals(WearableHelper.StartActivityPath)) {
            Intent startIntent = new Intent(this, LaunchActivity.class)
                    .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            this.startActivity(startIntent);
        } else if (messageEvent.getPath().equals(WearableHelper.BtDiscoverPath)) {
            this.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
                    .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 30));

            sendMessage(messageEvent.getSourceNodeId(), messageEvent.getPath(), stringToBytes(Build.MODEL));
        }
    }

    protected void sendMessage(String nodeID, String path, byte[] data) {
        try {
            Tasks.await(Wearable.getMessageClient(this).sendMessage(nodeID, path, data));
        } catch (Exception e) {
            Logger.writeLine(Log.ERROR, e);
        }
    }
}
