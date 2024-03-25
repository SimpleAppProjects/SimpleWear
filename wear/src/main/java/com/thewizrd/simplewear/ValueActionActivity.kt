package com.thewizrd.simplewear

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.helpers.showConfirmationOverlay
import com.thewizrd.simplewear.ui.simplewear.ValueActionScreen
import com.thewizrd.simplewear.viewmodels.ValueActionViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_UPDATECONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_ACTION
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_ACTIONDATA
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_STATUS
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

class ValueActionActivity : ComponentActivity() {
    companion object {
        const val EXTRA_STREAMTYPE = "SimpleWear.Droid.Wear.extra.STREAM_TYPE"
    }

    private val valueActionViewModel by viewModels<ValueActionViewModel>()

    private lateinit var remoteActivityHelper: RemoteActivityHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        remoteActivityHelper = RemoteActivityHelper(this)
        handleIntent(intent)

        setContent {
            ValueActionScreen()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.hasExtra(EXTRA_ACTION)) {
            val action = intent.getSerializableExtra(EXTRA_ACTION) as Actions

            when (action) {
                Actions.VOLUME -> { /* Valid action */
                }

                Actions.BRIGHTNESS -> { /* Valid action */
                }

                else -> {
                    // Not a ValueAction
                    setResult(RESULT_CANCELED)
                    finish()
                    return
                }
            }

            if (action == Actions.VOLUME && intent.hasExtra(EXTRA_STREAMTYPE)) {
                val streamType = intent.getSerializableExtra(EXTRA_STREAMTYPE) as? AudioStreamType
                    ?: AudioStreamType.MUSIC

                valueActionViewModel.onActionUpdated(action, streamType)
            } else {
                valueActionViewModel.onActionUpdated(action)
            }
        }
    }

    override fun onStart() {
        super.onStart()

        valueActionViewModel.initActivityContext(this)

        lifecycleScope.launch {
            valueActionViewModel.eventFlow.collect { event ->
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
                                        this@ValueActionActivity,
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
                                        this@ValueActionActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    WearableHelper.ActionsPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
                        val action = JSONParser.deserializer(jsonData, Action::class.java)

                        val actionSuccessful = action?.isActionSuccessful ?: false
                        val actionStatus = action?.actionStatus ?: ActionStatus.UNKNOWN

                        if (!actionSuccessful) {
                            lifecycleScope.launch {
                                when (actionStatus) {
                                    ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@ValueActionActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(getString(R.string.error_actionfailed))
                                            .showOn(this@ValueActionActivity)
                                    }

                                    ActionStatus.PERMISSION_DENIED -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@ValueActionActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(getString(R.string.error_permissiondenied))
                                            .showOn(this@ValueActionActivity)

                                        valueActionViewModel.openAppOnPhone(
                                            this@ValueActionActivity,
                                            false
                                        )
                                    }

                                    ActionStatus.TIMEOUT -> {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@ValueActionActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(getString(R.string.error_sendmessage))
                                            .showOn(this@ValueActionActivity)
                                    }

                                    ActionStatus.SUCCESS -> {}
                                    else -> {}
                                }
                            }
                        }
                    }

                    WearableHelper.AudioVolumePath, WearableHelper.ValueStatusSetPath -> {
                        val status = event.data.getSerializable(EXTRA_STATUS) as ActionStatus

                        when (status) {
                            ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            this@ValueActionActivity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(getString(R.string.error_actionfailed))
                                    .showOn(this@ValueActionActivity)
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                CustomConfirmationOverlay()
                                    .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                    .setCustomDrawable(
                                        ContextCompat.getDrawable(
                                            this@ValueActionActivity,
                                            R.drawable.ws_full_sad
                                        )
                                    )
                                    .setMessage(getString(R.string.error_permissiondenied))
                                    .showOn(this@ValueActionActivity)

                                valueActionViewModel.openAppOnPhone(this@ValueActionActivity, false)
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
        valueActionViewModel.refreshState()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}