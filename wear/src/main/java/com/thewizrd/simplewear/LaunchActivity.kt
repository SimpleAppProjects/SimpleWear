package com.thewizrd.simplewear

import android.app.Activity
import android.content.Intent
import android.os.Bundle

class LaunchActivity : Activity() {
    companion object {
        private const val TAG = "LaunchActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = Intent(this, PhoneSyncActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)

        // Navigate
        startActivity(intent)
        finishAffinity()
    }
}