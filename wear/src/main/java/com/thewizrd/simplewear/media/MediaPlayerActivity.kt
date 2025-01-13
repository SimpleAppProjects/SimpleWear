package com.thewizrd.simplewear.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.simplewear.MediaPlayer

class MediaPlayerActivity : ComponentActivity() {
    companion object {
        private const val KEY_APPDETAILS = "SimpleWear.Droid.extra.APP_DETAILS"
        private const val KEY_AUTOLAUNCH = "SimpleWear.Droid.extra.AUTO_LAUNCH"

        fun buildIntent(context: Context, appDetails: AppItemViewModel): Intent {
            return Intent(context, MediaPlayerActivity::class.java).apply {
                putExtra(
                    KEY_APPDETAILS,
                    JSONParser.serializer(appDetails, AppItemViewModel::class.java)
                )
            }
        }

        fun buildAutoLaunchIntent(context: Context): Intent {
            return Intent(context, MediaPlayerActivity::class.java).apply {
                putExtra(KEY_AUTOLAUNCH, true)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        var startDestination = Screen.MediaPlayer.autoLaunch()

        intent?.extras?.getString(KEY_APPDETAILS)?.let {
            val model = JSONParser.deserializer(it, AppItemViewModel::class.java)

            if (model != null && !model.key.isNullOrBlank()) {
                startDestination = Screen.MediaPlayer.getRoute(model)
            }
        }

        setContent {
            MediaPlayer(
                startDestination = startDestination
            )
        }
    }
}