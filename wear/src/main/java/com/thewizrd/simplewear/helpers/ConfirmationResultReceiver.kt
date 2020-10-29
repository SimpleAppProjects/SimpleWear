package com.thewizrd.simplewear.helpers

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.wearable.view.ConfirmationOverlay
import com.google.android.wearable.intent.RemoteIntent

class ConfirmationResultReceiver(private val activity: Activity?) : ResultReceiver(Handler()) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        if (resultCode == RemoteIntent.RESULT_OK) {
            if (activity != null) showConfirmationOverlay(true)
        } else if (resultCode == RemoteIntent.RESULT_FAILED) {
            if (activity != null) showConfirmationOverlay(false)
        } else {
            throw IllegalStateException("Unexpected result $resultCode")
        }
    }

    private fun showConfirmationOverlay(success: Boolean) {
        val overlay = ConfirmationOverlay()
        if (!success) overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION) else overlay.setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
        overlay.showOn(activity)
    }
}