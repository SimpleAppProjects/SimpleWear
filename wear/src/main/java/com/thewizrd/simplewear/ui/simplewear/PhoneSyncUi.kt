package com.thewizrd.simplewear.ui.simplewear

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.accompanist.drawablepainter.rememberDrawablePainter
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
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
        Scaffold(
            modifier = modifier.background(MaterialTheme.colors.background),
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            timeText = { TimeText() },
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

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
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
                    style = MaterialTheme.typography.button,
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
                    Button(
                        modifier = Modifier.requiredSize(36.dp),
                        onClick = onWifiButtonClicked,
                        colors = ButtonDefaults.primaryButtonColors(
                            backgroundColor = colorResource(id = R.color.colorPrimary)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_network_wifi_white_24dp),
                            contentDescription = stringResource(id = R.string.action_wifi)
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.requiredSize(44.dp),
                            trackColor = Color.Transparent,
                            strokeWidth = 4.dp
                        )
                    }

                    Button(
                        modifier = Modifier.requiredSize(36.dp),
                        onClick = onSyncButtonClicked,
                        colors = ButtonDefaults.primaryButtonColors(
                            backgroundColor = colorResource(id = R.color.colorPrimary)
                        )
                    ) {
                        Icon(
                            modifier = Modifier.size(24.dp),
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
                            contentDescription = null
                        )
                    }
                }

                if (uiState.showBTButton) {
                    Button(
                        modifier = Modifier.requiredSize(36.dp),
                        onClick = onBTButtonClicked,
                        colors = ButtonDefaults.primaryButtonColors(
                            backgroundColor = colorResource(id = R.color.colorPrimary)
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bluetooth_white_24dp),
                            contentDescription = stringResource(id = R.string.action_bt)
                        )
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