package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentHeight
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AnimatedPage
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextToggleButton
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.helpers.MediaHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.HorizontalPagerScreen
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.MediaPlayerListUiState
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch

@Composable
fun MediaPlayerListUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val mediaPlayerListViewModel = viewModel<MediaPlayerListViewModel>()
    val uiState by mediaPlayerListViewModel.uiState.collectAsState()

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
                MediaPlayerListScreen(
                    mediaPlayerListViewModel = mediaPlayerListViewModel,
                    confirmationViewModel = confirmationViewModel,
                    navController = navController
                )
            } else {
                MediaPlayerListSettings(
                    mediaPlayerListViewModel = mediaPlayerListViewModel
                )
            }
        }
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(context) {
        mediaPlayerListViewModel.initActivityContext(activity)
    }

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            mediaPlayerListViewModel.eventFlow.collect { event ->
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
                                mediaPlayerListViewModel.openPlayStore()

                                // Navigate
                                activity.startActivity(
                                    Intent(
                                        activity,
                                        PhoneSyncActivity::class.java
                                    )
                                )
                                activity.finishAffinity()
                            }

                            else -> {}
                        }
                    }

                    MediaHelper.MusicPlayersPath -> {
                        val status =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            confirmationViewModel.showOpenOnPhoneForFailure(
                                message = context.getString(
                                    R.string.error_permissiondenied
                                )
                            )

                            mediaPlayerListViewModel.openAppOnPhone(false)
                        }
                    }

                    MediaHelper.MediaPlayerAutoLaunchPath -> {
                        val status =
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.SUCCESS) {
                            navController.navigate(
                                Screen.MediaPlayer.autoLaunch(),
                                NavOptions.Builder()
                                    .setLaunchSingleTop(true)
                                    .setPopUpTo(Screen.MediaPlayer.route, true)
                                    .build()
                            )
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
        mediaPlayerListViewModel.refreshState()
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun MediaPlayerListScreen(
    mediaPlayerListViewModel: MediaPlayerListViewModel,
    confirmationViewModel: ConfirmationViewModel,
    navController: NavController
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by mediaPlayerListViewModel.uiState.collectAsState()

    MediaPlayerListScreen(
        uiState = uiState,
        onItemClicked = {
            lifecycleOwner.lifecycleScope.launch {
                val success = mediaPlayerListViewModel.startMediaApp(it)

                if (success) {
                    navController.navigate(
                        Screen.MediaPlayer.getRoute(it),
                        NavOptions.Builder()
                            .setLaunchSingleTop(true)
                            .setPopUpTo(Screen.MediaPlayer.route, true)
                            .build()
                    )
                } else {
                    confirmationViewModel.showFailure()
                }
            }
        },
        onRefresh = {
            mediaPlayerListViewModel.refreshState()
        }
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun MediaPlayerListScreen(
    uiState: MediaPlayerListUiState,
    onItemClicked: (AppItemViewModel) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    ScreenScaffold(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding
    ) { contentPadding ->
        LoadingContent(
            empty = uiState.mediaAppsSet.isEmpty(),
            emptyContent = {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .wrapContentHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically)
                    ) {
                        Text(
                            modifier = Modifier.padding(horizontal = 14.dp),
                            text = stringResource(id = R.string.error_nomusicplayers),
                            textAlign = TextAlign.Center
                        )
                        CompactButton(
                            label = {
                                Text(text = stringResource(id = R.string.action_refresh))
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
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
                        Text(text = stringResource(id = R.string.action_apps))
                    }
                }

                items(
                    items = uiState.mediaAppsSet.toList(),
                    key = { Pair(it.activityName, it.packageName) }
                ) { mediaItem ->
                    Button(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(text = mediaItem.appLabel ?: "")
                        },
                        icon = mediaItem.bitmapIcon?.let {
                            {
                                Icon(
                                    modifier = Modifier.requiredSize(ButtonDefaults.IconSize),
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = mediaItem.appLabel,
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        colors = if (mediaItem.key == uiState.activePlayerKey) {
                            ButtonDefaults.filledVariantButtonColors()
                        } else {
                            ButtonDefaults.filledTonalButtonColors()
                        },
                        onClick = {
                            onItemClicked(mediaItem)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPlayerListSettings(
    mediaPlayerListViewModel: MediaPlayerListViewModel
) {
    val uiState by mediaPlayerListViewModel.uiState.collectAsState()

    MediaPlayerListSettings(
        uiState = uiState,
        onCommitSelectedItems = {
            mediaPlayerListViewModel.updateFilteredApps(it)
        }
    )
}

@Composable
private fun MediaPlayerListSettings(
    uiState: MediaPlayerListUiState,
    onCommitSelectedItems: (Set<String>) -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

    var showFilterDialog by remember { mutableStateOf(false) }

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
                FilledTonalButton(
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text(text = stringResource(id = R.string.title_filter_apps))
                    },
                    onClick = {
                        showFilterDialog = true
                    },
                    icon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_baseline_filter_list_24),
                            contentDescription = stringResource(id = R.string.title_filter_apps)
                        )
                    }
                )
            }
        }

    }

    var selectedItems by remember(uiState.filteredAppsList) {
        mutableStateOf(uiState.filteredAppsList)
    }

    Dialog(
        modifier = Modifier
            .fillMaxSize(),
        visible = showFilterDialog,
        onDismissRequest = {
            onCommitSelectedItems.invoke(selectedItems)
            showFilterDialog = false
        }
    ) {
        MediaPlayerFilterScreen(
            uiState = uiState,
            selectedItems = selectedItems,
            onSelectedItemsChanged = {
                selectedItems = it
            },
            onDismissRequest = {
                onCommitSelectedItems.invoke(selectedItems)
                showFilterDialog = false
            }
        )
    }
}

@Composable
private fun MediaPlayerFilterScreen(
    uiState: MediaPlayerListUiState,
    selectedItems: Set<String> = emptySet(),
    onSelectedItemsChanged: (Set<String>) -> Unit = {},
    onDismissRequest: () -> Unit = {}
) {
    val columnState = rememberTransformingLazyColumnState()
    val contentPadding = rememberResponsiveColumnPadding(
        first = ColumnItemType.ListHeader,
        last = ColumnItemType.Button,
    )
    val transformationSpec = rememberTransformationSpec()

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
                Text(text = stringResource(id = R.string.title_filter_apps))
            }
        }

        items(uiState.allMediaAppsSet.toList()) {
            val isChecked = selectedItems.contains(it.packageName!!)

            TextToggleButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                checked = isChecked,
                onCheckedChange = { checked ->
                    onSelectedItemsChanged.invoke(
                        if (!checked) {
                            selectedItems.minusElement(it.packageName!!)
                        } else {
                            selectedItems.plusElement(it.packageName!!)
                        }
                    )
                },
                content = {
                    Text(
                        text = it.appLabel!!
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            FilledTonalButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = {
                    Text(text = stringResource(id = R.string.clear_all))
                },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_clear_all_24dp),
                        contentDescription = stringResource(id = R.string.clear_all)
                    )
                },
                onClick = {
                    onSelectedItemsChanged.invoke(emptySet())
                }
            )
        }

        item {
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .transformedHeight(this, transformationSpec),
                transformation = SurfaceTransformation(transformationSpec),
                label = {
                    Text(text = stringResource(id = android.R.string.ok))
                },
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_white_24dp),
                        contentDescription = stringResource(id = android.R.string.ok)
                    )
                },
                onClick = {
                    onDismissRequest.invoke()
                }
            )
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewNoContentMediaPlayerListScreen() {
    val uiState = remember {
        MediaPlayerListUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            allMediaAppsSet = emptySet(),
            mediaAppsSet = emptySet(),
            filteredAppsList = emptySet(),
            isLoading = false,
        )
    }

    MediaPlayerListScreen(uiState = uiState)
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaPlayerListScreen() {
    val context = LocalContext.current

    val allApps = remember {
        List(5) {
            AppItemViewModel().apply {
                appLabel = "App ${it + 1}"
                packageName = "com.package.${it}"
                bitmapIcon = ContextCompat.getDrawable(context, R.drawable.ic_icon)!!.toBitmap()
            }
        }.toSet()
    }

    val uiState = remember {
        MediaPlayerListUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            allMediaAppsSet = allApps,
            mediaAppsSet = allApps,
            filteredAppsList = emptySet(),
            isLoading = false,
        )
    }

    MediaPlayerListScreen(uiState = uiState)
}

@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaPlayerSettings() {
    val uiState = remember {
        MediaPlayerListUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            filteredAppsList = emptySet(),
            isLoading = false,
        )
    }

    MediaPlayerListSettings(uiState = uiState)
}

@OptIn(ExperimentalHorologistApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun PreviewMediaPlayerFilterScreen() {
    val context = LocalContext.current

    val allApps = remember {
        List(2) {
            AppItemViewModel().apply {
                appLabel = "App ${it + 1}"
                packageName = "com.package.${it}"
                bitmapIcon = ContextCompat.getDrawable(context, R.drawable.ic_icon)!!.toBitmap()
            }
        }.toSet()
    }

    val uiState = remember {
        MediaPlayerListUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            allMediaAppsSet = allApps,
            mediaAppsSet = emptySet(),
            filteredAppsList = setOf("com.package.0"),
            isLoading = false,
        )
    }

    MediaPlayerFilterScreen(
        uiState = uiState,
        selectedItems = uiState.filteredAppsList
    )
}