package com.thewizrd.simplewear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.ui.simplewear.AppLauncherScreen
import com.thewizrd.simplewear.viewmodels.AppLauncherViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class AppLauncherActivity : ComponentActivity() {
    private val appLauncherViewModel by viewModels<AppLauncherViewModel>()

    private lateinit var remoteActivityHelper: RemoteActivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteActivityHelper = RemoteActivityHelper(this)

        setContent {
            AppLauncherScreen()
        }
    }

    override fun onStart() {
        super.onStart()

        appLauncherViewModel.initActivityContext(this)

        lifecycleScope.launch {
            appLauncherViewModel.eventFlow.collect { event ->
                when (event.eventType) {
                    WearableListenerViewModel.ACTION_UPDATECONNECTIONSTATUS -> {
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
                                        this@AppLauncherActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                val intentAndroid = Intent(Intent.ACTION_VIEW)
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                    .setData(WearableHelper.getPlayStoreURI())

                                runCatching {
                                    remoteActivityHelper.startRemoteActivity(intentAndroid)
                                        .await()

                                    showConfirmationOverlay(true)
                                }.onFailure {
                                    if (it !is CancellationException) {
                                        showConfirmationOverlay(false)
                                    }
                                }

                                // Navigate
                                startActivity(
                                    Intent(
                                        this@AppLauncherActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            else -> {
                            }
                        }
                    }

                    WearableHelper.LaunchAppPath -> {
                        val status =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.SUCCESS_ANIMATION)
                                    .showOn(this@AppLauncherActivity)
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            this@AppLauncherActivity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(this@AppLauncherActivity.getString(R.string.error_permissiondenied))
                                    .showOn(this@AppLauncherActivity)

                                appLauncherViewModel.openAppOnPhone(this@AppLauncherActivity, false)
                            }

                            ActionStatus.FAILURE -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            this@AppLauncherActivity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(this@AppLauncherActivity.getString(R.string.error_actionfailed))
                                    .showOn(this@AppLauncherActivity)
                            }

                            else -> {}
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        appLauncherViewModel.refreshApps(true)
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}