package com.thewizrd.simplewear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.thewizrd.simplewear.ui.simplewear.PhoneSyncUi
import com.thewizrd.simplewear.viewmodels.PhoneSyncViewModel

class PhoneSyncActivity : ComponentActivity() {
    private val phoneSyncViewModel by viewModels<PhoneSyncViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContent {
            PhoneSyncUi()
        }
    }

    override fun onStart() {
        super.onStart()
        phoneSyncViewModel.initActivityContext(this)
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        phoneSyncViewModel.refreshConnectionStatus()
    }
}