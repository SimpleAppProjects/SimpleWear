package com.thewizrd.simplewear.helpers

import android.app.Activity
import androidx.fragment.app.Fragment
import androidx.wear.widget.ConfirmationOverlay

fun Activity.showConfirmationOverlay(success: Boolean) {
    val overlay = ConfirmationOverlay()
    if (!success) {
        overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION)
    } else {
        overlay.setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
    }
    overlay.showOn(this)
}

fun Fragment.showConfirmationOverlay(success: Boolean) {
    val overlay = ConfirmationOverlay()
    if (!success) {
        overlay.setType(ConfirmationOverlay.FAILURE_ANIMATION)
    } else {
        overlay.setType(ConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
    }
    overlay.showAbove(requireView())
}

fun Activity.showConfirmationOverlay(@ConfirmationOverlay.OverlayType type: Int) {
    val overlay = ConfirmationOverlay()
    overlay.setType(type)
    overlay.showOn(this)
}

fun Fragment.showConfirmationOverlay(@ConfirmationOverlay.OverlayType type: Int) {
    val overlay = ConfirmationOverlay()
    overlay.setType(type)
    overlay.showAbove(requireView())
}