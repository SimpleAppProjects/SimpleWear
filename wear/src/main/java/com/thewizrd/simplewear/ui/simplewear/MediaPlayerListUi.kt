package com.thewizrd.simplewear.ui.simplewear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.HierarchicalFocusCoordinator
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.SwipeToDismissBox
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.layout.PagerScaffold
import com.google.android.horologist.compose.layout.ScalingLazyColumn
import com.google.android.horologist.compose.layout.ScalingLazyColumnDefaults
import com.google.android.horologist.compose.layout.ScalingLazyColumnState
import com.google.android.horologist.compose.layout.rememberResponsiveColumnState
import com.google.android.horologist.compose.layout.scrollAway
import com.google.android.horologist.compose.material.ListHeaderDefaults
import com.google.android.horologist.compose.material.ResponsiveListHeader
import com.google.android.horologist.compose.pager.HorizontalPagerDefaults
import com.google.android.horologist.compose.rotaryinput.rotaryWithScroll
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.MediaPlayerListUiState
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel

@OptIn(
    ExperimentalHorologistApi::class, ExperimentalFoundationApi::class,
    ExperimentalWearFoundationApi::class
)
@Composable
fun MediaPlayerListUi(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val mediaPlayerListViewModel = activityViewModel<MediaPlayerListViewModel>()
    val uiState by mediaPlayerListViewModel.uiState.collectAsState()

    val scrollState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Unspecified,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        )
    )
    val swipeToDismissBoxState = rememberSwipeToDismissBoxState()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { 2 }
    )

    WearAppTheme {
        PagerScaffold(
            modifier = Modifier.fillMaxSize(),
            timeText = {
                if (pagerState.currentPage == 0) {
                    TimeText(modifier = Modifier.scrollAway { scrollState })
                }
            },
            pagerState = if (uiState.isLoading) null else pagerState
        ) {
            SwipeToDismissBox(
                modifier = Modifier.background(MaterialTheme.colors.background),
                onDismissed = {
                    activity.onBackPressed()
                },
                state = swipeToDismissBoxState
            ) { isBackground ->
                if (isBackground) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background)
                    )
                } else {
                    HorizontalPager(
                        modifier = modifier.edgeSwipeToDismiss(swipeToDismissBoxState),
                        state = pagerState,
                        flingBehavior = HorizontalPagerDefaults.flingParams(pagerState)
                    ) { pageIdx ->
                        HierarchicalFocusCoordinator(requiresFocus = { pageIdx == pagerState.currentPage }) {
                            if (pageIdx == 0) {
                                MediaPlayerListScreen(
                                    mediaPlayerListViewModel = mediaPlayerListViewModel,
                                    scrollState = scrollState
                                )
                            } else {
                                MediaPlayerListSettings(
                                    mediaPlayerListViewModel = mediaPlayerListViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun MediaPlayerListScreen(
    mediaPlayerListViewModel: MediaPlayerListViewModel,
    scrollState: ScalingLazyColumnState
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val uiState by mediaPlayerListViewModel.uiState.collectAsState()

    MediaPlayerListScreen(
        uiState = uiState,
        scrollState = scrollState,
        onItemClicked = {
            mediaPlayerListViewModel.startMediaApp(activity, it)
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
    scrollState: ScalingLazyColumnState = rememberResponsiveColumnState(),
    onItemClicked: (AppItemViewModel) -> Unit = {},
    onRefresh: () -> Unit = {}
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
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
                        CompactChip(
                            label = {
                                Text(text = stringResource(id = R.string.action_refresh))
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_baseline_refresh_24),
                                    contentDescription = null
                                )
                            },
                            onClick = onRefresh
                        )
                    }
                }
            },
            loading = uiState.isLoading
        ) {
            ScalingLazyColumn(
                modifier = Modifier.fillMaxSize(),
                columnState = scrollState,
            ) {
                item {
                    ResponsiveListHeader(contentPadding = ListHeaderDefaults.firstItemPadding()) {
                        Text(text = stringResource(id = R.string.action_apps))
                    }
                }

                items(
                    items = uiState.mediaAppsSet.toList(),
                    key = { Pair(it.activityName, it.packageName) }
                ) {
                    Chip(
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(text = it.appLabel ?: "")
                        },
                        icon = it.bitmapIcon?.let {
                            {
                                Icon(
                                    modifier = Modifier.requiredSize(ChipDefaults.IconSize),
                                    bitmap = it.asImageBitmap(),
                                    contentDescription = null,
                                    tint = Color.Unspecified
                                )
                            }
                        },
                        colors = ChipDefaults.secondaryChipColors(),
                        onClick = {
                            onItemClicked(it)
                        }
                    )
                }
            }

            PositionIndicator(scalingLazyListState = scrollState.state)
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
        onCheckChanged = {
            Settings.setAutoLaunchMediaCtrls(it)
        },
        onCommitSelectedItems = {
            mediaPlayerListViewModel.updateFilteredApps(it)
        }
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun MediaPlayerListSettings(
    uiState: MediaPlayerListUiState,
    onCheckChanged: (Boolean) -> Unit = {},
    onCommitSelectedItems: (Set<String>) -> Unit = {}
) {
    val scrollState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Unspecified,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        )
    )

    var showFilterDialog by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        columnState = scrollState
    ) {
        item {
            ResponsiveListHeader(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ListHeaderDefaults.firstItemPadding()
            ) {
                Text(text = stringResource(id = R.string.title_settings))
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(id = R.string.title_filter_apps))
                },
                onClick = {
                    showFilterDialog = true
                },
                colors = ChipDefaults.secondaryChipColors(),
                icon = {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_baseline_filter_list_24),
                        contentDescription = null
                    )
                }
            )
        }
        item {
            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(text = stringResource(id = R.string.title_autolaunchmediactrls))
                },
                checked = uiState.isAutoLaunchEnabled,
                onCheckedChange = onCheckChanged,
                toggleControl = {
                    Switch(checked = uiState.isAutoLaunchEnabled)
                }
            )
        }
    }

    val dialogScrollState = rememberResponsiveColumnState(
        contentPadding = ScalingLazyColumnDefaults.padding(
            first = ScalingLazyColumnDefaults.ItemType.Unspecified,
            last = ScalingLazyColumnDefaults.ItemType.Chip,
        )
    )

    var selectedItems by remember(uiState.filteredAppsList) {
        mutableStateOf(uiState.filteredAppsList)
    }

    Dialog(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
        showDialog = showFilterDialog,
        onDismissRequest = {
            onCommitSelectedItems.invoke(selectedItems)
            showFilterDialog = false
        },
        scrollState = dialogScrollState.state
    ) {
        MediaPlayerFilterScreen(
            uiState = uiState,
            dialogScrollState = dialogScrollState,
            selectedItems = selectedItems,
            onSelectedItemsChanged = {
                selectedItems = it
            },
            onDismissRequest = {
                onCommitSelectedItems.invoke(selectedItems)
                showFilterDialog = false
            }
        )

        LaunchedEffect(showFilterDialog) {
            dialogScrollState.state.scrollToItem(0)
        }
    }
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun MediaPlayerFilterScreen(
    uiState: MediaPlayerListUiState,
    dialogScrollState: ScalingLazyColumnState = rememberResponsiveColumnState(),
    selectedItems: Set<String> = emptySet(),
    onSelectedItemsChanged: (Set<String>) -> Unit = {},
    onDismissRequest: () -> Unit = {}
) {
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .rotaryWithScroll(dialogScrollState),
        columnState = dialogScrollState,
    ) {
        item {
            ResponsiveListHeader(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = ListHeaderDefaults.firstItemPadding()
            ) {
                Text(text = stringResource(id = R.string.title_filter_apps))
            }
        }

        items(uiState.allMediaAppsSet.toList()) {
            val isChecked = selectedItems.contains(it.packageName!!)

            ToggleChip(
                modifier = Modifier.fillMaxWidth(),
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
                label = {
                    Text(
                        text = it.appLabel!!
                    )
                },
                toggleControl = {
                    Checkbox(
                        checked = isChecked
                    )
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
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
                },
                colors = ChipDefaults.secondaryChipColors(
                    backgroundColor = MaterialTheme.colors.onBackground,
                    contentColor = MaterialTheme.colors.background
                )
            )
        }

        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
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
                },
                colors = ChipDefaults.primaryChipColors()
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
            isAutoLaunchEnabled = false
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
            isAutoLaunchEnabled = false
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
            isAutoLaunchEnabled = false
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
            isAutoLaunchEnabled = false
        )
    }

    MediaPlayerFilterScreen(
        uiState = uiState,
        selectedItems = uiState.filteredAppsList
    )
}