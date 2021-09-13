package com.thewizrd.simplewear.helpers

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import androidx.wear.widget.ConfirmationOverlay
import com.google.android.wearable.intent.RemoteIntent

class ConfirmationResultReceiver(private val activity: Activity?) : ResultReceiver(Handler()) {
    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
        when (resultCode) {
            RemoteIntent.RESULT_OK -> {
                activity?.showConfirmationOverlay(true)
            }
            RemoteIntent.RESULT_FAILED -> {
                activity?.showConfirmationOverlay(false)
            }
            else -> {
                throw IllegalStateException("Unexpected result $resultCode")
            }
        }
    }

    private fun Activity.showConfirmationOverlay(success: Boolean) {
        val overlay = ConfirmationOverlay()
        if (!success) overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION) else overlay.setType(
            ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION
        )
        overlay.showOn(this)
    }
}