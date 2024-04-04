package com.thewizrd.simplewear.media

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.simplewear.MediaPlayerUi
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_UPDATECONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.launch

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

    private val mediaPlayerViewModel by viewModels<MediaPlayerViewModel>()

    private var isAutoLaunch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        handleIntent(intent)

        setContent {
            MediaPlayerUi()
        }
    }

    override fun onStart() {
        super.onStart()

        mediaPlayerViewModel.initActivityContext(this)

        lifecycleScope.launch {
            mediaPlayerViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                WearableListenerViewModel.EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> {
                                // Navigate
                                startActivity(
                                    Intent(
                                        this@MediaPlayerActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                mediaPlayerViewModel.openPlayStore(this@MediaPlayerActivity)

                                // Navigate
                                startActivity(
                                    Intent(
                                        this@MediaPlayerActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    MediaHelper.MediaPlayerConnectPath,
                    MediaHelper.MediaPlayerAutoLaunchPath -> {
                        val actionStatus = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.PERMISSION_DENIED) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@MediaPlayerActivity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(getString(R.string.error_permissiondenied))
                                .showOn(this@MediaPlayerActivity)

                            mediaPlayerViewModel.openAppOnPhone(this@MediaPlayerActivity, false)
                        }
                    }

                    MediaHelper.MediaPlayPath -> {
                        val actionStatus = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        if (actionStatus == ActionStatus.TIMEOUT) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(R.drawable.ws_full_sad)
                                .setMessage(R.string.error_playback_failed)
                                .showOn(this@MediaPlayerActivity)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        mediaPlayerViewModel.refreshStatus()
    }

    override fun onPause() {
        mediaPlayerViewModel.requestPlayerDisconnect()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)

        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.extras?.getBoolean(KEY_AUTOLAUNCH) == true) {
            isAutoLaunch = true
            return
        }

        val model = intent.extras?.getString(KEY_APPDETAILS)?.let {
            JSONParser.deserializer(it, AppItemViewModel::class.java)
        }

        if (model != null) {
            mediaPlayerViewModel.updateMediaPlayerDetails(model)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}