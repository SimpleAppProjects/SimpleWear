package com.thewizrd.simplewear.ui.simplewear

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import androidx.preference.PreferenceManager
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import com.google.android.horologist.compose.layout.scrollAway
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.utils.ErrorMessage
import com.thewizrd.simplewear.viewmodels.DashboardViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch

@Composable
fun Dashboard(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val dashboardViewModel = viewModel<DashboardViewModel>()

    val scrollState = rememberScrollState()
    var stateRefreshed by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        timeText = {
            TimeText(modifier = Modifier.scrollAway { scrollState })
        },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scrollState = scrollState) }
    ) {
        DashboardScreen(
            dashboardViewModel = dashboardViewModel,
            scrollState = scrollState,
            navController = navController
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) {}

    val sharedPreferenceListener = remember {
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                Settings.KEY_LAYOUTMODE -> {
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            lifecycleOwner.withStarted {
                                dashboardViewModel.updateLayout(Settings.useGridLayout())
                            }
                        }
                    }
                }

                Settings.KEY_DASHCONFIG -> {
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            lifecycleOwner.withStarted {
                                dashboardViewModel.resetDashboard()
                            }
                        }
                    }
                }

                Settings.KEY_SHOWBATSTATUS -> {
                    lifecycleOwner.lifecycleScope.launch {
                        runCatching {
                            lifecycleOwner.withStarted {
                                dashboardViewModel.showBatteryState(Settings.isShowBatStatus())
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(context) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .registerOnSharedPreferenceChangeListener(sharedPreferenceListener)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (PermissionChecker.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (PermissionChecker.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) != PermissionChecker.PERMISSION_GRANTED ||
                PermissionChecker.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PermissionChecker.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_ADVERTISE,
                        Manifest.permission.BLUETOOTH_SCAN
                    )
                )
            }
        }

        dashboardViewModel.initActivityContext(activity)
    }

    DisposableEffect(context) {
        onDispose {
            PreferenceManager.getDefaultSharedPreferences(context)
                .unregisterOnSharedPreferenceChangeListener(sharedPreferenceListener)
        }
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            dashboardViewModel.eventFlow.collect { event ->
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
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            WearConnectionStatus.CONNECTING -> {}
                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                dashboardViewModel.openPlayStore(activity)

                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            WearConnectionStatus.CONNECTED -> {}
                        }
                    }

                    WearableHelper.ActionsPath -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)
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
                                                activity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(activity.getString(R.string.error_actionfailed))
                                        .showOn(activity)
                                }

                                ActionStatus.PERMISSION_DENIED -> {
                                    if (action.actionType == Actions.TORCH) {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    activity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(activity.getString(R.string.error_torch_action))
                                            .showOn(activity)
                                    } else if (action.actionType == Actions.SLEEPTIMER) {
                                        // Open store on device
                                        val intentAndroid = Intent(Intent.ACTION_VIEW)
                                            .addCategory(Intent.CATEGORY_BROWSABLE)
                                            .setData(SleepTimerHelper.getPlayStoreURI())

                                        if (intentAndroid.resolveActivity(activity.packageManager) != null) {
                                            activity.startActivity(intentAndroid)
                                            Toast.makeText(
                                                activity,
                                                R.string.error_sleeptimer_notinstalled,
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            CustomConfirmationOverlay()
                                                .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                                .setCustomDrawable(
                                                    ContextCompat.getDrawable(
                                                        activity,
                                                        R.drawable.ws_full_sad
                                                    )
                                                )
                                                .setMessage(
                                                    activity.getString(
                                                        R.string.error_sleeptimer_notinstalled
                                                    )
                                                )
                                                .showOn(activity)
                                        }
                                    } else {
                                        CustomConfirmationOverlay()
                                            .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                            .setCustomDrawable(
                                                ContextCompat.getDrawable(
                                                    activity,
                                                    R.drawable.ws_full_sad
                                                )
                                            )
                                            .setMessage(activity.getString(R.string.error_permissiondenied))
                                            .showOn(activity)
                                    }

                                    dashboardViewModel.openAppOnPhone(activity, false)
                                }

                                ActionStatus.TIMEOUT -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                activity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(activity.getString(R.string.error_sendmessage))
                                        .showOn(activity)
                                }

                                ActionStatus.REMOTE_FAILURE -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                activity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(activity.getString(R.string.error_remoteactionfailed))
                                        .showOn(activity)
                                }

                                ActionStatus.REMOTE_PERMISSION_DENIED -> {
                                    CustomConfirmationOverlay()
                                        .setType(CustomConfirmationOverlay.CUSTOM_ANIMATION)
                                        .setCustomDrawable(
                                            ContextCompat.getDrawable(
                                                activity,
                                                R.drawable.ws_full_sad
                                            )
                                        )
                                        .setMessage(activity.getString(R.string.error_permissiondenied))
                                        .showOn(activity)
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

        lifecycleOwner.lifecycleScope.launch {
            dashboardViewModel.errorMessagesFlow.collect { error ->
                when (error) {
                    is ErrorMessage.String -> {
                        Toast.makeText(activity, error.message, Toast.LENGTH_SHORT).show()
                    }

                    is ErrorMessage.Resource -> {
                        Toast.makeText(activity, error.stringId, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        // Update statuses
        if (!stateRefreshed) {
            dashboardViewModel.refreshStatus()
            stateRefreshed = true
        }

        onPauseOrDispose {
            stateRefreshed = false
        }
    }
}