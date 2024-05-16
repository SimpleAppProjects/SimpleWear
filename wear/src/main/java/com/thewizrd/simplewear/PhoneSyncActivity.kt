package com.thewizrd.simplewear

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.thewizrd.simplewear.ui.simplewear.PhoneSyncUi
import com.thewizrd.simplewear.utils.ErrorMessage
import com.thewizrd.simplewear.viewmodels.PhoneSyncViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_OPENONPHONE
import kotlinx.coroutines.launch

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

        lifecycleScope.launch {
            phoneSyncViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    ACTION_OPENONPHONE -> {
                        Toast.makeText(
                            this@PhoneSyncActivity,
                            R.string.error_syncing,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }

        lifecycleScope.launch {
            phoneSyncViewModel.errorMessagesFlow.collect { error ->
                when (error) {
                    is ErrorMessage.String -> {
                        Toast.makeText(applicationContext, error.message, Toast.LENGTH_SHORT).show()
                    }

                    is ErrorMessage.Resource -> {
                        Toast.makeText(applicationContext, error.stringId, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        phoneSyncViewModel.refreshConnectionStatus()
    }
}