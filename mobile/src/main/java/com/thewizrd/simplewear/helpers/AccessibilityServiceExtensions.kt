package com.thewizrd.simplewear.helpers

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.accessibilityservice.GestureDescription.StrokeDescription
import android.graphics.Path

fun AccessibilityService.dispatchScrollUp(ratio: Float? = null) {
    val displayMetrics = this.applicationContext.resources.displayMetrics

    val height = displayMetrics.heightPixels.toFloat()
    val centerY = height / 2f
    val centerX = displayMetrics.widthPixels / 2f
    val distance = height / 8f

    val path = Path().apply {
        moveTo(centerX, centerY)
        lineTo(centerX, centerY + centerY)
    }

    dispatchGesture(path)
}

fun AccessibilityService.dispatchScrollDown(ratio: Float? = null) {
    val displayMetrics = this.applicationContext.resources.displayMetrics

    val height = displayMetrics.heightPixels.toFloat()
    val centerY = height / 2f
    val centerX = displayMetrics.widthPixels / 2f
    val distance = height / 8f

    val path = Path().apply {
        moveTo(centerX, centerY)
        lineTo(centerX, 0f)
    }

    dispatchGesture(path)
}

fun AccessibilityService.dispatchScrollLeft(ratio: Float? = null) {
    val displayMetrics = this.applicationContext.resources.displayMetrics

    val width = displayMetrics.widthPixels.toFloat()
    val centerY = displayMetrics.heightPixels / 2f
    val centerX = width / 2f
    val distance = width / 8f

    val path = Path().apply {
        moveTo(centerX, centerY)
        lineTo(centerX + centerX, centerY)
    }

    dispatchGesture(path)
}

fun AccessibilityService.dispatchScrollRight(ratio: Float? = null) {
    val displayMetrics = this.applicationContext.resources.displayMetrics

    val width = displayMetrics.widthPixels.toFloat()
    val centerY = displayMetrics.heightPixels / 2f
    val centerX = width / 2f
    val distance = width / 8f

    val path = Path().apply {
        moveTo(centerX, centerY)
        lineTo(centerX + -centerX, centerY)
    }

    dispatchGesture(path)
}

private fun AccessibilityService.dispatchGesture(path: Path) {
    val gesture = GestureDescription.Builder().apply {
        addStroke(StrokeDescription(path, 0, 500))
    }.build()

    dispatchGesture(gesture)
}

private fun AccessibilityService.dispatchGesture(gesture: GestureDescription) {
    this.dispatchGesture(gesture, null, null)
}