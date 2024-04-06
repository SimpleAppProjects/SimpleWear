package com.thewizrd.simplewear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.simplewear.SimpleWearApp
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_ACTION

class DashboardActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var startDestination = Screen.Dashboard.route

        if (intent?.hasExtra(EXTRA_ACTION) == true) {
            val actionType = intent.getIntExtra(EXTRA_ACTION, 0).let {
                Actions.valueOf(it)
            }

            when (actionType) {
                Actions.PHONE -> startDestination = Screen.CallManager.route
                else -> {}
            }
        }

        setContent {
            SimpleWearApp(
                startDestination = startDestination
            )
        }
    }
}