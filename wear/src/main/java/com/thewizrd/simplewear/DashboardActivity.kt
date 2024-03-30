package com.thewizrd.simplewear

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withStarted
import androidx.preference.PreferenceManager
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.simplewear.Dashboard
import com.thewizrd.simplewear.utils.ErrorMessage
import com.thewizrd.simplewear.viewmodels.DashboardViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.ACTION_UPDATECONNECTIONSTATUS
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_ACTIONDATA
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel.Companion.EXTRA_CONNECTIONSTATUS
import kotlinx.coroutines.launch

class DashboardActivity : ComponentActivity(), OnSharedPreferenceChangeListener {
    private val dashboardViewModel by viewModels<DashboardViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        PreferenceManager.getDefaultSharedPreferences(this@DashboardActivity)
            .registerOnSharedPreferenceChangeListener(this@DashboardActivity)

        setContent {
            Dashboard()
        }
    }

    override fun onStart() {
        super.onStart()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (PermissionChecker.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_ADVERTISE), 0)
            }
        }

        dashboardViewModel.initActivityContext(this)

        lifecycleScope.launch {
            dashboardViewModel.eventFlow.collect { event ->
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
                                startActivity(
                                    Intent(
                                        this@DashboardActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            WearConnectionStatus.CONNECTING -> {}
                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                dashboardViewModel.openPlayStore(this@DashboardActivity)

                                // Navigate
                                startActivity(
                                    Intent(
                                        this@DashboardActivity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                finishAffinity()
                            }

                            WearConnectionStatus.CONNECTED -> {}
                        }
                    }

                    WearableHelper.ActionsPath -> {
                        val jsonData = event.data.getString(EXTRA_ACTIONDATA)
                        val action = JSONParser.deserializer(jsonData, Action::class.java)!!

                        dashboardViewModel.cancelTimer(action.actionType)
                        dashboardViewModel.updateButton(ActionButtonViewModel(action))

                        val actionStatus = action.actionStatus

                        if (!action.isActionSuccessful) {
                            when (actionStatus) {
                                ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                this@DashboardActivity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(this@DashboardActivity.getString(R.string.error_actionfailed))
                                        .showOn(this@DashboardActivity)
                                }

                                ActionStatus.PERMISSION_DENIED -> {
                                    if (action.actionType == Actions.TORCH) {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_torch_action))
                                            .showOn(this@DashboardActivity)
                                    } else if (action.actionType == Actions.SLEEPTIMER) {
                                        // Open store on device
                                        val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(SleepTimerHelper.getPlayStoreURI())

                                        if (intentAndroid.resolveActivity(packageManager) != null) {
                                            startActivity(intentAndroid)
                                            Toast.makeText(
                                                this@DashboardActivity,
                                                R.string.error_sleeptimer_notinstalled,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        this@DashboardActivity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(
                                                    this@DashboardActivity.getString(
                                                        R.string.error_sleeptimer_notinstalled
                                                    )
                                                )
                                                .showOn(this@DashboardActivity)
                                        }
                                    } else {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    this@DashboardActivity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(this@DashboardActivity.getString(R.string.error_permissiondenied))
                                            .showOn(this@DashboardActivity)
                                    }

                                    dashboardViewModel.openAppOnPhone(this@DashboardActivity, false)
                                }

                                ActionStatus.TIMEOUT -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                this@DashboardActivity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(this@DashboardActivity.getString(R.string.error_sendmessage))
                                        .showOn(this@DashboardActivity)
                                }

                                ActionStatus.REMOTE_FAILURE -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                this@DashboardActivity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(this@DashboardActivity.getString(R.string.error_remoteactionfailed))
                                        .showOn(this@DashboardActivity)
                                }

                                ActionStatus.REMOTE_PERMISSION_DENIED -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                this@DashboardActivity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(this@DashboardActivity.getString(R.string.error_permissiondenied))
                                        .showOn(this@DashboardActivity)
                                }

                                ActionStatus.SUCCESS -> {
                                }
                            }
                        }

                        // Re-enable click action
                        dashboardViewModel.setActionsClickable(true)
                    }
                }
            }
        }

        lifecycleScope.launch {
            dashboardViewModel.errorMessagesFlow.collect { error ->
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
        dashboardViewModel.refreshStatus()
    }

    override fun onStop() {
        super.onStop()
    }

    override fun onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(this)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroy()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        when (key) {
            Settings.KEY_LAYOUTMODE -> {
                lifecycleScope.launch {
                    runCatching {
                        withStarted {
                            dashboardViewModel.updateLayout(Settings.useGridLayout())
                        }
                    }
                }
            }

            Settings.KEY_DASHCONFIG -> {
                lifecycleScope.launch {
                    runCatching {
                        withStarted {
                            dashboardViewModel.resetDashboard()
                        }
                    }
                }
            }

            Settings.KEY_SHOWBATSTATUS -> {
                lifecycleScope.launch {
                    runCatching {
                        withStarted {
                            dashboardViewModel.showBatteryState(Settings.isShowBatStatus())
                        }
                    }
                }
            }
        }
    }
}