package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAll
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.getSerializableCompat
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.HorizontalPagerScreen
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.viewmodels.AppLauncherUiState
import com.thewizrd.simplewear.viewmodels.AppLauncherViewModel
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch

@Composable
fun AppLauncherScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current

    val appLauncherViewModel = viewModel<AppLauncherViewModel>()
    val uiState by appLauncherViewModel.uiState.collectAsState()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )

    HorizontalPagerScreen(
        modifier = modifier,
        pagerState = pagerState,
        hidePagerIndicator = uiState.isLoading,
    ) { pageIdx ->
        AnimatedPage(pageIdx, pagerState) {
            if (pageIdx == 0) {
                AppLauncherScreen(
                    appLauncherViewModel = appLauncherViewModel
                )
            } else {
                AppLauncherSettings(
                    appLauncherViewModel = appLauncherViewModel
                )
            }
        }
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(context) {
        appLauncherViewModel.initActivityContext(activity)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
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
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            WearConnectionStatus.APPNOTINSTALLED -> {
                                // Open store on remote device
                                appLauncherViewModel.openPlayStore()

                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            else -> {
                            }
                        }
                    }

                    WearableHelper.LaunchAppPath -> {
                        val status =
                            event.data.getSerializableCompat(
                                WearableListenerViewModel.EXTRA_STATUS,
                                ActionStatus::class.java
                            )

                        when (status) {
                            ActionStatus.SUCCESS -> {
                                confirmationViewModel.showSuccess()
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                confirmationViewModel.showFailure(message = context.getString(R.string.error_permissiondenied))

                                appLauncherViewModel.openAppOnPhone(false)
                            }

                            ActionStatus.FAILURE -> {
                                confirmationViewModel.showFailure(
                                    message = context.getString(R.string.error_actionfailed)
                                )
                            }

                            else -> {}
                        }
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
    }

    LaunchedEffect(Unit) {
        // Update statuses
        appLauncherViewModel.refreshApps()
    }
}

@Composable
private fun AppLauncherScreen(
    appLauncherViewModel: AppLauncherViewModel
) {
    val uiState by appLauncherViewModel.uiState.collectAsState()

    AppLauncherScreen(
        uiState = uiState,
        onItemClicked = {
            appLauncherViewModel.openRemoteApp(it)
        },
        onRefresh = {
            appLauncherViewModel.refreshApps()
        }
    )

    LaunchedEffect(uiState.loadAppIcons) {
        if (!uiState.isLoading && uiState.loadAppIcons && uiState.appsList.isNotEmpty() && uiState.appsList.fastAll { it.bitmapIcon == null }) {
            appLauncherViewModel.refreshApps()
        }
    }
}

@Composable
private fun AppLauncherScreen(
    uiState: AppLauncherUiState,
    scrollState: TransformingLazyColumnState = rememberTransformingLazyColumnState(),
    onItemClicked: (AppItemViewModel) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button
    )

    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = scrollState,
        contentPadding = contentPadding
    ) { contentPadding ->
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            LoadingContent(
                empty = uiState.appsList.isEmpty(),
                emptyContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .wrapContentHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                text = stringResource(id = R.string.error_noapps),
                                textAlign = TextAlign.Center
                            )
                            CompactButton(
                                label = {
                                    Text(text = stringResource(id = R.string.action_refresh))
                                },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = stringResource(id = R.string.action_refresh)
                                    )
                                },
                                onClick = onRefresh
                            )
                        }
                    }
                },
                loading = uiState.isLoading
            ) {
                TransformingLazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = scrollState,
                    contentPadding = contentPadding
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec)
                        ) {
                            Text(text = stringResource(id = R.string.action_apps))
                        }
                    }

                    items(
                        items = uiState.appsList,
                        key = { Pair(it.activityName, it.packageName) }
                    ) { appItem ->
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            label = {
                                Text(text = appItem.appLabel ?: "")
                            },
                            icon = if (uiState.loadAppIcons) {
                                appItem.bitmapIcon?.let {
                                    {
                                        Icon(
                                            modifier = Modifier.requiredSize(ButtonDefaults.IconSize),
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = appItem.appLabel,
                                            tint = Color.Unspecified
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                            onClick = {
                                onItemClicked(appItem)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppLauncherSettings(
    appLauncherViewModel: AppLauncherViewModel
) {
    val uiState by appLauncherViewModel.uiState.collectAsState()

    AppLauncherSettings(
        uiState = uiState,
        onCheckChanged = {
            appLauncherViewModel.showAppIcons(it)
        }
    )
}

@Composable
private fun AppLauncherSettings(
    uiState: AppLauncherUiState,
    onCheckChanged: (Boolean) -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()

    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        scrollState = columnState,
        contentPadding = contentPadding
    ) { contentPadding ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = contentPadding
        ) {
            item {
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec)
                ) {
                    Text(text = stringResource(id = R.string.title_settings))
                }
            }
            item {
                SwitchButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(id = R.string.pref_loadicons_title))
                    },
                    checked = uiState.loadAppIcons,
                    onCheckedChange = onCheckChanged
                )
            }
        }
    }
}

@WearPreviewDevices
@Composable
private fun PreviewAppLauncherScreen() {
    val context = LocalContext.current

    val uiState = remember(context) {
        AppLauncherUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            appsList = List(10) { index ->
                AppItemViewModel().apply {
                    appLabel = "App ${index + 1}"
                    packageName = "com.package.${index}"
                    bitmapIcon = ContextCompat.getDrawable(context, R.drawable.ic_icon)!!.toBitmap()
                }
            },
            isLoading = false,
            loadAppIcons = true
        )
    }

    AppLauncherScreen(uiState = uiState)
}

@WearPreviewDevices
@Composable
private fun PreviewLoadingAppLauncherScreen() {
    val context = LocalContext.current

    val uiState = remember(context) {
        AppLauncherUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            appsList = emptyList(),
            isLoading = true,
            loadAppIcons = true
        )
    }

    AppLauncherScreen(uiState = uiState)
}

@WearPreviewDevices
@Composable
private fun PreviewNoContentAppLauncherScreen() {
    val context = LocalContext.current

    val uiState = remember(context) {
        AppLauncherUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            appsList = emptyList(),
            isLoading = false,
            loadAppIcons = true
        )
    }

    AppLauncherScreen(uiState = uiState)
}

@WearPreviewDevices
@Composable
private fun PreviewAppLauncherSettings() {
    val uiState = remember {
        AppLauncherUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            appsList = emptyList(),
            isLoading = true,
            loadAppIcons = true
        )
    }

    AppLauncherSettings(uiState = uiState)
}