package com.thewizrd.simplewear.ui.simplewear

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ArcProgressIndicator
import androidx.wear.compose.material3.ArcProgressIndicatorDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.viewmodels.PhoneSyncUiState
import com.thewizrd.simplewear.viewmodels.PhoneSyncViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun PhoneSyncUi(
    modifier: Modifier = Modifier
) {
    val phoneSyncViewModel = activityViewModel<PhoneSyncViewModel>()

    WearAppTheme {
        AppScaffold(
            modifier = modifier
        ) {
            PhoneSyncUi(phoneSyncViewModel)
        }
    }
}

@Composable
private fun PhoneSyncUi(
    phoneSyncViewModel: PhoneSyncViewModel
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by phoneSyncViewModel.uiState.collectAsState()

    val bluetoothRequestLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == Activity.RESULT_OK) {
                lifecycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                    delay(2000)
                    phoneSyncViewModel.showProgressBar()
                    delay(10000)
                    if (isActive) {
                        phoneSyncViewModel.showProgressBar(false)
                    }
                }
            }
        }

    val permissionRequestLauncher =
        rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach { (permission, granted) ->
                when (permission) {
                    Manifest.permission.BLUETOOTH_CONNECT -> {
                        if (granted) {
                            bluetoothRequestLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                        }
                    }
                }
            }
        }

    PhoneSyncUi(
        uiState = uiState,
        onBTButtonClicked = {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && PermissionChecker.checkSelfPermission(
                        context,
                        Manifest.permission.BLUETOOTH_CONNECT
                    ) != PermissionChecker.PERMISSION_GRANTED
                ) {
                    permissionRequestLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
                } else {
                    bluetoothRequestLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                }
            }
        },
        onWifiButtonClicked = {
            phoneSyncViewModel.openWifiSettings(activity)
        },
        onSyncButtonClicked = {
            when (uiState.connectionStatus) {
                WearConnectionStatus.DISCONNECTED -> {
                    phoneSyncViewModel.refreshConnectionStatus()
                }

                WearConnectionStatus.APPNOTINSTALLED -> {
                    lifecycleOwner.lifecycleScope.launch {
                        phoneSyncViewModel.openPlayStore(activity)
                    }
                }

                else -> {}
            }
        }
    )
}

@Composable
private fun PhoneSyncUi(
    uiState: PhoneSyncUiState,
    onWifiButtonClicked: () -> Unit = {},
    onBTButtonClicked: () -> Unit = {},
    onSyncButtonClicked: () -> Unit = {},
) {
    val context = LocalContext.current
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(
        modifier = Modifier.fillMaxSize()
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize()
        ) {
            ListHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        top = if (isRound) 32.dp else 8.dp,
                        start = 14.dp,
                        end = 14.dp
                    )
            ) {
                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = when (uiState.connectionStatus) {
                        WearConnectionStatus.DISCONNECTED -> {
                            stringResource(id = R.string.status_disconnected)
                        }

                        WearConnectionStatus.CONNECTING -> {
                            stringResource(id = R.string.status_connecting)
                        }

                        WearConnectionStatus.APPNOTINSTALLED -> {
                            stringResource(id = R.string.error_notinstalled)
                        }

                        WearConnectionStatus.CONNECTED -> {
                            stringResource(id = R.string.status_connected)
                        }

                        null -> {
                            stringResource(id = R.string.message_gettingstatus)
                        }
                    },
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                if (uiState.showWifiButton) {
                    FilledIconButton(
                        modifier = Modifier.requiredSize(IconButtonDefaults.ExtraSmallButtonSize),
                        onClick = onWifiButtonClicked
                    ) {
                        Icon(
                            modifier = Modifier.requiredSize(IconButtonDefaults.SmallIconSize - 4.dp),
                            painter = painterResource(id = R.drawable.ic_network_wifi_white_24dp),
                            contentDescription = stringResource(id = R.string.action_wifi)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (!isRound) {
                        Box {
                            var isVisible by remember { mutableStateOf(true) }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = isVisible,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.requiredSize(IconButtonDefaults.ExtraSmallButtonSize + 12.dp),
                                    strokeWidth = 4.dp,
                                    colors = ProgressIndicatorDefaults.colors(
                                        trackColor = Color.Transparent,
                                        indicatorColor = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            LaunchedEffect(uiState.isLoading) {
                                if (!uiState.isLoading) {
                                    delay(500)
                                }
                                if (isActive) {
                                    isVisible = uiState.isLoading
                                }
                            }
                        }
                    }

                    FilledIconButton(
                        modifier = Modifier.requiredSize(IconButtonDefaults.ExtraSmallButtonSize),
                        onClick = onSyncButtonClicked
                    ) {
                        Icon(
                            modifier = Modifier.requiredSize(IconButtonDefaults.SmallIconSize - 4.dp),
                            painter = when (uiState.connectionStatus) {
                                WearConnectionStatus.DISCONNECTED -> {
                                    painterResource(id = R.drawable.ic_phonelink_erase_white_24dp)
                                }

                                WearConnectionStatus.CONNECTING, WearConnectionStatus.CONNECTED -> {
                                    val drawable = remember(context) {
                                        ContextCompat.getDrawable(
                                            context,
                                            android.R.drawable.ic_popup_sync
                                        )
                                    }
                                    rememberDrawablePainter(
                                        drawable = drawable
                                    )
                                }

                                WearConnectionStatus.APPNOTINSTALLED -> {
                                    painterResource(id = R.drawable.common_full_open_on_phone)
                                }

                                null -> painterResource(id = R.drawable.ic_sync_24dp)
                            },
                            contentDescription = when (uiState.connectionStatus) {
                                WearConnectionStatus.DISCONNECTED -> stringResource(R.string.status_disconnected)
                                WearConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
                                WearConnectionStatus.APPNOTINSTALLED -> stringResource(R.string.error_notinstalled)
                                WearConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
                                null -> null
                            }
                        )
                    }
                }

                if (uiState.showBTButton) {
                    FilledIconButton(
                        modifier = Modifier.requiredSize(IconButtonDefaults.ExtraSmallButtonSize),
                        onClick = onBTButtonClicked
                    ) {
                        Icon(
                            modifier = Modifier.requiredSize(IconButtonDefaults.SmallIconSize - 4.dp),
                            painter = painterResource(id = R.drawable.ic_bluetooth_white_24dp),
                            contentDescription = stringResource(id = R.string.action_bt)
                        )
                    }
                }
            }
        }

        if (isRound) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = contentPadding.calculateBottomPadding() / 2),
            ) {
                var isVisible by remember { mutableStateOf(true) }

                AnimatedVisibility(
                    visible = isVisible,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ArcProgressIndicator(
                        modifier =
                            Modifier.size(ArcProgressIndicatorDefaults.recommendedIndeterminateDiameter)
                    )
                }

                LaunchedEffect(uiState.isLoading) {
                    if (!uiState.isLoading) {
                        delay(500)
                    }
                    if (isActive) {
                        isVisible = uiState.isLoading
                    }
                }
            }
        }
    }
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewPhoneSyncUi() {
    val uiState = remember {
        PhoneSyncUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            isLoading = true,
            showWifiButton = true,
            showBTButton = true
        )
    }

    PhoneSyncUi(uiState = uiState)
}