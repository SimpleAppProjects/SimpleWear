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
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.ui.simplewear.CallManagerUi
import com.thewizrd.simplewear.viewmodels.CallManagerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_UPDATECONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class CallManagerActivity : ComponentActivity() {
    private val callManagerViewModel by viewModels<CallManagerViewModel>()

    private lateinit var remoteActivityHelper: RemoteActivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteActivityHelper = RemoteActivityHelper(this)

        setContent {
            CallManagerUi()
        }
    }

    override fun onStart() {
        super.onStart()

        lifecycleScope.launch {
            callManagerViewModel.eventFlow.collect { event ->
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
                                        this@CallManagerActivity,
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
                                        this@CallManagerActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    InCallUIHelper.CallStatePath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            CustomConfirmationOverlay()
                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                .setCustomDrawable(
                                    ContextCompat.getDrawable(
                                        this@CallManagerActivity,
                                        R.drawable.ws_full_sad
                                    )
                                )
                                .setMessage(getString(R.string.error_permissiondenied))
                                .showOn(this@CallManagerActivity)

                            callManagerViewModel.openAppOnPhone(this@CallManagerActivity, false)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        // Update statuses
        callManagerViewModel.refreshCallState()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}