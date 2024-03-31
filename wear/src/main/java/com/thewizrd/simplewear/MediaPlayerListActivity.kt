package com.thewizrd.simplewear

import android.annotation.SuppressLint
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
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.ui.simplewear.MediaPlayerListUi
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_UPDATECONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_CONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.launch

class MediaPlayerListActivity : ComponentActivity() {
    private val mediaPlayerListViewModel by viewModels<MediaPlayerListViewModel>()

    @SuppressLint("UseSwitchCompatOrMaterialCode")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MediaPlayerListUi()
        }

        lifecycleScope.launchWhenResumed {
            mediaPlayerListViewModel.autoLaunchMediaControls()
        }
    }

    override fun onStart() {
        super.onStart()

        mediaPlayerListViewModel.initActivityContext(this)

        lifecycleScope.launch {
            mediaPlayerListViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    ACTION_UPDATECONNECTIONSTATUS -> {
                        val connectionStatus = WearConnectionStatus.valueOf(
                            event.data.getInt(
                                EXTRA_CONNECTIONSTATUS,
                                0
                            )
                        )

                        when (connectionStatus) {
                            WearConnectionStatus.DISCONNECTED -> {
                                // Navigate
                                startActivity(
                                    Intent(
                                        this@MediaPlayerListActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                mediaPlayerListViewModel.openPlayStore(this@MediaPlayerListActivity)

                                // Navigate
                                startActivity(
                                    Intent(
                                        this@MediaPlayerListActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    MediaHelper.MusicPlayersPath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@MediaPlayerListActivity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(getString(R.string.error_permissiondenied))
                                .showOn(this@MediaPlayerListActivity)

                            mediaPlayerListViewModel.openAppOnPhone(
                                this@MediaPlayerListActivity,
                                false
                            )
                        }
                    }

                    MediaHelper.MediaPlayerAutoLaunchPath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.SUCCESS) {
                            startActivity(MediaPlayerActivity.buildAutoLaunchIntent(this@MediaPlayerListActivity))
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        mediaPlayerListViewModel.refreshState(true)
    }
}