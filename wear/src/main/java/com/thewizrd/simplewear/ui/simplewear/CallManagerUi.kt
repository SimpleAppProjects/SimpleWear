package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Dialpad
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.SpeakerPhone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.material3.ripple
import androidx.wear.compose.material3.touchTargetAwareSize
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.google.android.horologist.audio.ui.material3.VolumeLevelIndicator
import com.google.android.horologist.audio.ui.material3.volumeRotaryBehavior
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.getSerializableCompat
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.components.ConfirmationOverlay
import com.thewizrd.simplewear.ui.components.ElapsedTimeSource
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.ui.tools.WearPreviewDevices
import com.thewizrd.simplewear.ui.utils.rememberFocusRequester
import com.thewizrd.simplewear.viewmodels.CallManagerUiState
import com.thewizrd.simplewear.viewmodels.CallManagerViewModel
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationViewModel
import com.thewizrd.simplewear.viewmodels.ValueActionViewModel
import com.thewizrd.simplewear.viewmodels.ValueActionVolumeViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt
import kotlin.random.Random

@Composable
fun CallManagerUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val callManagerViewModel = viewModel<CallManagerViewModel>()
    val uiState by callManagerViewModel.uiState.collectAsState()

    val valueActionViewModel = viewModel<ValueActionViewModel>()
    val volumeViewModel = remember(context, valueActionViewModel) {
        ValueActionVolumeViewModel(context, valueActionViewModel)
    }
    val volumeUiState by volumeViewModel.volumeUiState.collectAsState()

    val confirmationViewModel = viewModel<ConfirmationViewModel>()
    val confirmationData by confirmationViewModel.confirmationEventsFlow.collectAsState()

    ScreenScaffold(
        modifier = modifier,
        scrollIndicator = {
            VolumeLevelIndicator(
                volumeUiState = { volumeUiState },
                displayIndicatorEvents = volumeViewModel.displayIndicatorEvents
            )
        }
    ) { contentPadding ->
        LoadingContent(
            empty = !uiState.isCallActive,
            emptyContent = {
                NoCallActiveScreen(
                    modifier = Modifier.padding(contentPadding)
                )
            },
            loading = uiState.isLoading
        ) {
            CallManagerUi(
                modifier = Modifier.padding(contentPadding),
                callManagerViewModel = callManagerViewModel,
                volumeViewModel = volumeViewModel,
                navController = navController
            )
        }
    }

    ConfirmationOverlay(
        confirmationData = confirmationData,
        onTimeout = { confirmationViewModel.clearFlow() },
    )

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycleScope.launch {
            callManagerViewModel.eventFlow.collect { event ->
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
                                callManagerViewModel.openPlayStore()

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

                    InCallUIHelper.ConnectPath -> {
                        val status =
                            event.data.getSerializableCompat(
                                WearableListenerViewModel.EXTRA_STATUS,
                                ActionStatus::class.java
                            )

                        if (status == ActionStatus.PERMISSION_DENIED) {
                            confirmationViewModel.showOpenOnPhoneForFailure(
                                message = context.getString(
                                    R.string.error_permissiondenied
                                )
                            )

                            callManagerViewModel.openAppOnPhone(false)
                        }
                    }

                    WearableHelper.AudioVolumePath, WearableHelper.ValueStatusSetPath -> {
                        val status =
                            event.data.getSerializableCompat(
                                WearableListenerViewModel.EXTRA_STATUS,
                                ActionStatus::class.java
                            )

                        when (status) {
                            ActionStatus.UNKNOWN, ActionStatus.FAILURE -> {
                                confirmationViewModel.showFailure(message = context.getString(R.string.error_actionfailed))
                            }

                            ActionStatus.PERMISSION_DENIED -> {
                                confirmationViewModel.showFailure(message = context.getString(R.string.error_permissiondenied))

                                valueActionViewModel.openAppOnPhone(false)
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

    LaunchedEffect(lifecycleOwner) {
        // Update statuses
        valueActionViewModel.onActionUpdated(Actions.VOLUME, AudioStreamType.VOICE_CALL)

        callManagerViewModel.refreshCallState()
        valueActionViewModel.refreshState()
    }
}

@Composable
fun CallManagerUi(
    modifier: Modifier = Modifier,
    callManagerViewModel: CallManagerViewModel,
    volumeViewModel: ValueActionVolumeViewModel,
    navController: NavController
) {
    val uiState by callManagerViewModel.uiState.collectAsState()
    val volumeUiState by volumeViewModel.volumeUiState.collectAsState()

    var showKeyPadUi by remember { mutableStateOf(false) }

    CallManagerUi(
        modifier = modifier
            .requestFocusOnHierarchyActive()
            .rotaryScrollable(
                focusRequester = rememberFocusRequester(),
                behavior = volumeRotaryBehavior(
                    volumeUiStateProvider = { volumeUiState },
                    onRotaryVolumeInput = { newValue -> volumeViewModel.setVolume(newValue) }
                )
            ),
        uiState = uiState,
        onShowKeypadUi = {
            showKeyPadUi = true
        },
        onMute = {
            callManagerViewModel.setMuteEnabled(!uiState.isMuted)
        },
        onSpeakerPhone = {
            callManagerViewModel.enableSpeakerphone(!uiState.isSpeakerPhoneOn)
        },
        onVolume = {
            navController.navigate(
                Screen.ValueAction.getRoute(Actions.VOLUME, AudioStreamType.VOICE_CALL)
            )
        },
        onEndCall = {
            callManagerViewModel.endCall()
        }
    )

    Dialog(
        modifier = Modifier.fillMaxSize(),
        visible = showKeyPadUi,
        onDismissRequest = { showKeyPadUi = false }
    ) {
        KeypadScreen(
            onKeyPressed = { digit ->
                callManagerViewModel.requestSendDTMFTone(digit)
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun CallManagerUi(
    modifier: Modifier = Modifier,
    uiState: CallManagerUiState,
    onMute: () -> Unit = {},
    onShowKeypadUi: () -> Unit = {},
    onSpeakerPhone: () -> Unit = {},
    onVolume: () -> Unit = {},
    onEndCall: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    val isRound = LocalConfiguration.current.isScreenRound

    val isLargeHeight = LocalConfiguration.current.screenHeightDp >= 225
    val isLargeWidth = LocalConfiguration.current.screenWidthDp >= 225

    val buttonSize = if (isLargeWidth || isLargeHeight) {
        IconButtonDefaults.SmallButtonSize
    } else {
        40.dp
    }

    val buttonRowPadding = if (isRound) 16.dp else 8.dp

    var showMenuDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.callerBitmap != null) {
            val colorScheme = MaterialTheme.colorScheme
            Image(
                bitmap = uiState.callerBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.desc_contact_photo),
                contentScale = ContentScale.Crop,
                alpha = 0.6f,
                modifier =
                    modifier
                        .fillMaxSize()
                        .drawWithCache {
                            val gradientBrush =
                                Brush.radialGradient(
                                    0.65f to Color.Transparent,
                                    1f to colorScheme.background,
                                )
                            onDrawWithContent {
                                drawRect(colorScheme.background)
                                drawContent()
                                drawRect(color = colorScheme.primaryContainer, alpha = 0.3f)
                                drawRect(color = colorScheme.onPrimary, alpha = 0.6f)
                                drawRect(gradientBrush)
                            }
                        },
            )
        }

        if (isPreview) {
            TimeText()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(ListHeaderDefaults.ContentPadding),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    text = uiState.callerName ?: stringResource(id = R.string.message_callactive),
                    style = if (isLargeWidth || isLargeHeight) {
                        MaterialTheme.typography.labelLarge
                    } else {
                        MaterialTheme.typography.labelMedium
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center
                )
            }

            if (uiState.callStartTime > -1L) {
                val timerSource = remember(uiState.callStartTime) {
                    ElapsedTimeSource(uiState.callStartTime)
                }

                Text(
                    modifier = Modifier.fillMaxWidth(),
                    text = timerSource.currentTime(),
                    style = if (isLargeWidth || isLargeHeight) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodySmall
                    },
                    textAlign = TextAlign.Center
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = buttonRowPadding, end = buttonRowPadding, top = 8.dp)
                    .weight(1f, fill = true),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                CallUiButton(
                    imageVector = Icons.Rounded.MicOff,
                    buttonSize = buttonSize,
                    isChecked = uiState.isMuted,
                    onClick = onMute,
                    contentDescription = if (uiState.isMuted) {
                        stringResource(R.string.volstate_muted)
                    } else {
                        stringResource(R.string.label_mute)
                    }
                )

                if (uiState.canSendDTMFKeys || uiState.supportsSpeaker) {
                    CallUiButton(
                        imageVector = Icons.Rounded.MoreHoriz,
                        buttonSize = buttonSize,
                        onClick = { showMenuDialog = true },
                        contentDescription = stringResource(R.string.action_volume)
                    )
                } else {
                    CallUiButton(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        buttonSize = buttonSize,
                        onClick = onVolume,
                        contentDescription = stringResource(R.string.action_volume)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (buttonSize > 40.dp) 4.dp else 0.dp),
        ) {
            FilledIconButton(
                modifier = Modifier.touchTargetAwareSize(buttonSize),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                onClick = onEndCall
            ) {
                Icon(
                    modifier = Modifier.size(IconButtonDefaults.iconSizeFor(buttonSize)),
                    imageVector = Icons.Rounded.CallEnd,
                    contentDescription = stringResource(id = R.string.action_hangup)
                )
            }
        }
    }

    Dialog(
        visible = showMenuDialog,
        onDismissRequest = { showMenuDialog = false }
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
                        Text(text = stringResource(R.string.title_callcontroller))
                    }
                }

                if (uiState.canSendDTMFKeys) {
                    item {
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            label = {
                                Text(text = stringResource(R.string.label_keypad))
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.Dialpad,
                                    contentDescription = stringResource(R.string.label_keypad)
                                )
                            },
                            onClick = {
                                onShowKeypadUi()
                                showMenuDialog = false
                            }
                        )
                    }
                }

                if (uiState.supportsSpeaker) {
                    item {
                        FilledTonalButton(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                            label = {
                                Text(
                                    text = if (uiState.isSpeakerPhoneOn) {
                                        stringResource(R.string.desc_speakerphone_on)
                                    } else {
                                        stringResource(R.string.desc_speakerphone_off)
                                    }
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Rounded.SpeakerPhone,
                                    contentDescription = if (uiState.isSpeakerPhoneOn) {
                                        stringResource(R.string.desc_speakerphone_on)
                                    } else {
                                        stringResource(R.string.desc_speakerphone_off)
                                    }
                                )
                            },
                            colors = if (uiState.isSpeakerPhoneOn) {
                                ButtonDefaults.buttonColors()
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            },
                            onClick = onSpeakerPhone
                        )
                    }
                }

                item {
                    FilledTonalButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, transformationSpec),
                        transformation = SurfaceTransformation(transformationSpec),
                        label = {
                            Text(text = stringResource(R.string.action_volume))
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = stringResource(R.string.action_volume)
                            )
                        },
                        onClick = {
                            onVolume()
                            showMenuDialog = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CallUiButton(
    modifier: Modifier = Modifier,
    buttonSize: Dp = IconButtonDefaults.DefaultButtonSize,
    isChecked: Boolean = false,
    imageVector: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit = {}
) {
    FilledIconButton(
        modifier = modifier.touchTargetAwareSize(buttonSize),
        onClick = onClick,
        colors = if (isChecked) {
            IconButtonDefaults.filledIconButtonColors()
        } else {
            IconButtonDefaults.filledTonalIconButtonColors()
        }
    ) {
        Icon(
            modifier = Modifier.requiredSize(IconButtonDefaults.iconSizeFor(buttonSize)),
            imageVector = imageVector,
            contentDescription = contentDescription
        )
    }
}

@WearPreviewDevices
@Composable
private fun NoCallActiveScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 14.dp),
            text = stringResource(id = R.string.message_nocall_active),
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@WearPreviewDevices
@WearPreviewFontScales
@Composable
private fun KeypadScreen(
    onKeyPressed: (Char) -> Unit = {}
) {
    val config = LocalConfiguration.current

    val isPreview = LocalInspectionMode.current
    val isRound = config.isScreenRound
    val isLargeWidth = config.screenWidthDp >= 225

    val headerPadding: PaddingValues = remember(config) {
        if (isRound) {
            val screenHeightDp = config.screenHeightDp
            val screenWidthDp = config.smallestScreenWidthDp
            val maxSquareEdge = (sqrt(((screenHeightDp * screenWidthDp) / 2).toDouble()))
            val inset = Dp(((screenHeightDp - maxSquareEdge) / 2).toFloat())
            PaddingValues(
                start = inset, top = inset, end = inset,
                bottom = ListHeaderDefaults.ContentPadding.calculateBottomPadding()
            )
        } else {
            ListHeaderDefaults.ContentPadding
        }
    }

    var keypadText by remember { mutableStateOf("") }
    val digits by remember {
        derivedStateOf {
            listOf(
                '1', '2', '3',
                '4', '5', '6',
                '7', '8', '9',
                '*', '0', '#'
            )
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(headerPadding)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier.wrapContentWidth(align = Alignment.End, unbounded = true),
                    text = if (isPreview) "01234567891110123" else keypadText,
                    style = MaterialTheme.typography.bodyMedium,
                    letterSpacing = 1.5.sp,
                    fontSize = if (isLargeWidth) 14.sp else 12.sp,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Visible,
                    softWrap = true
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(0.65f)
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        bottom = 8.dp
                    ),
                maxItemsInEachRow = 3,
                maxLines = 4,
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                digits.forEach {
                    Box(
                        modifier = Modifier
                            .requiredHeightIn(max = 32.dp)
                            .weight(1f, fill = true)
                            .clickable(
                                role = Role.Button,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    radius = 20.dp,
                                    bounded = false
                                )
                            ) {
                                keypadText += it
                                onKeyPressed.invoke(it)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            modifier = Modifier
                                .fillMaxSize()
                                .align(Alignment.Center),
                            text = "$it",
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge
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
private fun PreviewCallManagerUi() {
    val bmp = remember {
        Bitmap.createBitmap(intArrayOf(0x50400080), 1, 1, Bitmap.Config.ARGB_8888)
    }

    val uiState = remember {
        CallManagerUiState(
            connectionStatus = WearConnectionStatus.CONNECTED,
            callerName = if (Random.nextInt(0, 2) == 1) {
                "(123) 456-7890"
            } else {
                null
            },
            callerBitmap = bmp,
            callStartTime = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(60),
            isSpeakerPhoneOn = true,
            isCallActive = true,
            isMuted = true,
            supportsSpeaker = true,
            canSendDTMFKeys = true
        )
    }

    CallManagerUi(uiState = uiState)
}
