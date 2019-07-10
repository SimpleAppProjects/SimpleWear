package com.thewizrd.simplewear.helpers;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.support.wearable.view.ConfirmationOverlay;

import com.google.android.wearable.intent.RemoteIntent;

public class ConfirmationResultReceiver extends ResultReceiver {
    private Activity activity;

    public ConfirmationResultReceiver(Activity activity) {
        super(new Handler());
        this.activity = activity;
    }

    @Override
    protected void onReceiveResult(int resultCode, Bundle resultData) {
        if (resultCode == RemoteIntent.RESULT_OK) {
            if (activity != null)
                showConfirmationOverlay(true);
        } else if (resultCode == RemoteIntent.RESULT_FAILED) {
            if (activity != null)
                showConfirmationOverlay(false);
        } else {
            throw new IllegalStateException("Unexpected result " + resultCode);
        }
    }

    private void showConfirmationOverlay(boolean success) {
        ConfirmationOverlay overlay = new ConfirmationOverlay();

        if (!success)
            overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION);
        else
            overlay.setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION);

        overlay.showOn(activity);
    }
}
