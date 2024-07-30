package com.thewizrd.simplewear.services

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.view.accessibility.AccessibilityEvent

class WearAccessibilityService : AccessibilityService() {
    override fun onCreate() {
        super.onCreate()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        private var instance: AccessibilityService? = null

        fun getInstance(): AccessibilityService? {
            return instance
        }

        fun isServiceBound(): Boolean {
            return instance != null
        }
    }
}