package com.thewizrd.simplewear.ui.simplewear

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.withStarted
import androidx.navigation.NavController
import androidx.preference.PreferenceManager
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.rememberColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.AlertContent
import com.google.android.horologist.compose.material.AlertDialog
import com.google.android.horologist.compose.material.Chip
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.updates.InAppUpdateManager
import com.thewizrd.simplewear.utils.ErrorMessage
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.DashboardViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalHorologistApi::class, ExperimentalAnimationGraphicsApi::class)
@Composable
fun Dashboard(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val dashboardViewModel = viewModel<DashboardViewModel>()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    val scrollState = rememberScrollState()
    var stateRefreshed by remember { mutableStateOf(false) }

    val inAppUpdateMgr = remember(context) {
        InAppUpdateManager.create(context)
    }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var showAppUpdateConfirmation by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        timeText = { TimeText(modifier = Modifier.scrollAway { scrollState }) },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scrollState = scrollState) }
    ) {
        DashboardScreen(
            dashboardViewModel = dashboardViewModel,
            scrollState = scrollState,
            navController = navController
        )
    }

    AlertDialog(
        showDialog = showUpdateDialog,
        onDismiss = {
            Settings.setLastUpdateCheckTime(Instant.now())
            showUpdateDialog = false
        },
        icon = {
            Icon(
                painter = rememberVectorPainter(image = Icons.Default.Info),
                contentDescription = null
            )
        },
        message = stringResource(id = R.string.message_wearappupdate_available)
    ) {
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }
        item {
            Chip(
                modifier = Modifier.padding(horizontal = 16.dp),
                label = stringResource(id = R.string.action_update),
                onClick = {
                    runCatching {
                        // Open store on device
                        activity.startActivity(
                            Intent(Intent.ACTION_VIEW)
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                .setData(WearableHelper.getPlayStoreURI())
                        )
                    }
                    showUpdateDialog = false
                }
            )
        }
        if (inAppUpdateMgr.updatePriority <= 3) {
            item {
                Chip(
                    label = stringResource(id = android.R.string.cancel),
                    onClick = {
                        Settings.setLastUpdateCheckTime(Instant.now())
                        showUpdateDialog = false
                    },
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }

    if (showAppUpdateConfirmation) {
        var startAnim by remember { mutableStateOf(false) }
        val dialogScrollState = rememberColumnState(
            ScalingLazyColumnDefaults.responsive(),
        )

        Dialog(
            showDialog = showAppUpdateConfirmation,
            onDismissRequest = {
                Settings.setLastUpdateCheckTime(Instant.now())
                showAppUpdateConfirmation = false
            },
            scrollState = dialogScrollState.state
        ) {
            AlertContent(
                icon = {
                    Icon(
                        modifier = Modifier.size(36.dp),
                        painter = rememberAnimatedVectorPainter(
                            animatedImageVector = AnimatedImageVector.animatedVectorResource(id = R.drawable.open_on_phone_animation),
                            atEnd = startAnim
                        ),
                        contentDescription = null
                    )
                },
                message = stringResource(id = R.string.message_phoneappupdate_available),
                onOk = {
                    Settings.setLastUpdateCheckTime(Instant.now())
                    showAppUpdateConfirmation = false
                },
                state = dialogScrollState
            )
        }

        LaunchedEffect(showAppUpdateConfirmation) {
            delay(250)
            startAnim = true
        }
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

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
                                    confirmationViewModel.showFailure(
                                        message = context.getString(R.string.error_actionfailed)
                                    )
                                }

                                ActionStatus.PERMISSION_DENIED -> {
                                    if (action.actionType == Actions.TORCH) {
                                        confirmationViewModel.showConfirmation(
                                            ConfirmationData(
                                                title = context.getString(R.string.error_torch_action)
                                            )
                                        )
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
                                            confirmationViewModel.showConfirmation(
                                                ConfirmationData(
                                                    title = context.getString(R.string.error_sleeptimer_notinstalled)
                                                )
                                            )
                                        }
                                    } else {
                                        confirmationViewModel.showConfirmation(
                                            ConfirmationData(
                                                title = context.getString(R.string.error_permissiondenied)
                                            )
                                        )
                                    }

                                    dashboardViewModel.openAppOnPhone(activity, false)
                                }

                                ActionStatus.TIMEOUT -> {
                                    confirmationViewModel.showConfirmation(
                                        ConfirmationData(
                                            title = context.getString(R.string.error_sendmessage)
                                        )
                                    )
                                }

                                ActionStatus.REMOTE_FAILURE -> {
                                    confirmationViewModel.showConfirmation(
                                        ConfirmationData(
                                            title = context.getString(R.string.error_remoteactionfailed)
                                        )
                                    )
                                }

                                ActionStatus.REMOTE_PERMISSION_DENIED -> {
                                    confirmationViewModel.showConfirmation(
                                        ConfirmationData(
                                            title = context.getString(R.string.error_permissiondenied)
                                        )
                                    )
                                }

                                ActionStatus.SUCCESS -> {
                                }
                            }
                        }

                        // Re-enable click action
                        dashboardViewModel.setActionsClickable(true)
                    }

                    WearableListenerViewModel.ACTION_SHOWCONFIRMATION -> {
                        val jsonData =
                            event.data.getString(WearableListenerViewModel.EXTRA_ACTIONDATA)

                        JSONParser.deserializer(jsonData, ConfirmationData::class.java)?.let {
                            confirmationViewModel.showConfirmation(it)
                        }
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

        lifecycleOwner.lifecycleScope.launch {
            if (Duration.between(
                    Settings.getLastUpdateCheckTime(),
                    Instant.now()
                ) >= Duration.ofDays(1)
            ) {
                // Check phone version
                runCatching {
                    val phoneVersionCode = withTimeoutOrNull(15000) {
                        dashboardViewModel.requestPhoneAppVersion()
                    }

                    phoneVersionCode?.let {
                        showAppUpdateConfirmation = !WearableHelper.isAppUpToDate(it)
                        if (showAppUpdateConfirmation) {
                            dashboardViewModel.openPlayStore(activity, false)
                        }
                    }
                }
            }
        }

        lifecycleOwner.lifecycleScope.launch {
            if (Duration.between(
                    Settings.getLastUpdateCheckTime(),
                    Instant.now()
                ) >= Duration.ofDays(1)
            ) {
                // Check phone version
                runCatching {
                    showUpdateDialog =
                        inAppUpdateMgr.checkIfUpdateAvailable() && inAppUpdateMgr.updatePriority > 3
                }.onFailure {
                    Logger.writeLine(Log.ERROR, it)
                }
            }
        }
    }

    LifecycleResumeEffect(Unit, lifecycleOwner = lifecycleOwner) {
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