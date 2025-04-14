package com.thewizrd.simplewear.ui.simplewear

import android.content.Intent
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredSizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import androidx.wear.compose.material.ripple
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.helpers.InCallUIHelper
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.CustomConfirmationOverlay
import com.thewizrd.simplewear.ui.components.LoadingContent
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.activityViewModel
import com.thewizrd.simplewear.ui.theme.findActivity
import com.thewizrd.simplewear.viewmodels.CallManagerUiState
import com.thewizrd.simplewear.viewmodels.CallManagerViewModel
import com.thewizrd.simplewear.viewmodels.WearableListenerViewModel
import kotlinx.coroutines.launch

@Composable
fun CallManagerUi(
    modifier: Modifier = Modifier,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val lifecycleOwner = LocalLifecycleOwner.current
    val callManagerViewModel = activityViewModel<CallManagerViewModel>()
    val uiState by callManagerViewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.background(MaterialTheme.colors.background),
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        timeText = {
            if (!uiState.isLoading) TimeText()
        },
    ) {
        LoadingContent(
            empty = !uiState.isCallActive,
            emptyContent = {
                NoCallActiveScreen()
            },
            loading = uiState.isLoading
        ) {
            CallManagerUi(
                callManagerViewModel = callManagerViewModel,
                navController = navController
            )
        }
    }

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
                                callManagerViewModel.openPlayStore(activity)

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
                            event.data.getSerializable(WearableListenerViewModel.EXTRA_STATUS) as ActionStatus

                        if (status == ActionStatus.PERMISSION_DENIED) {
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

                            callManagerViewModel.openAppOnPhone(activity, false)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        // Update statuses
        callManagerViewModel.refreshCallState()
    }
}

@Composable
fun CallManagerUi(
    callManagerViewModel: CallManagerViewModel,
    navController: NavController
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    val uiState by callManagerViewModel.uiState.collectAsState()

    var showKeyPadUi by remember { mutableStateOf(false) }

    CallManagerUi(
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
        showDialog = showKeyPadUi,
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
    uiState: CallManagerUiState,
    onMute: () -> Unit = {},
    onShowKeypadUi: () -> Unit = {},
    onSpeakerPhone: () -> Unit = {},
    onVolume: () -> Unit = {},
    onEndCall: () -> Unit = {}
) {
    val isPreview = LocalInspectionMode.current
    val isRound = LocalConfiguration.current.isScreenRound

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (isPreview) {
            TimeText()
        }

        if (uiState.callerBitmap != null) {
            Image(
                modifier = Modifier.fillMaxSize(),
                bitmap = uiState.callerBitmap.asImageBitmap(),
                contentDescription = stringResource(R.string.desc_contact_photo)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(5.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxWidth()
                    .padding(horizontal = if (isRound) 32.dp else 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    modifier = Modifier
                        .wrapContentHeight()
                        .basicMarquee(iterations = Int.MAX_VALUE),
                    text = uiState.callerName ?: stringResource(id = R.string.message_callactive),
                    maxLines = 1,
                    overflow = TextOverflow.Visible,
                    textAlign = TextAlign.Center
                )
            }

            FlowRow(
                modifier = Modifier
                    .fillMaxWidth(),
                maxItemsInEachRow = 2,
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CallUiButton(
                    iconResourceId = R.drawable.ic_mic_off_24dp,
                    isChecked = uiState.isMuted,
                    onClick = onMute,
                    contentDescription = if (uiState.isMuted) {
                        stringResource(R.string.volstate_muted)
                    } else {
                        stringResource(R.string.label_mute)
                    }
                )
                if (uiState.canSendDTMFKeys) {
                    CallUiButton(
                        iconResourceId = R.drawable.ic_dialpad_24dp,
                        onClick = onShowKeypadUi,
                        contentDescription = stringResource(R.string.label_keypad)
                    )
                }
                if (uiState.supportsSpeaker) {
                    CallUiButton(
                        iconResourceId = R.drawable.ic_baseline_speaker_phone_24,
                        isChecked = uiState.isSpeakerPhoneOn,
                        onClick = onSpeakerPhone,
                        contentDescription = if (uiState.isSpeakerPhoneOn) {
                            stringResource(R.string.desc_speakerphone_on)
                        } else {
                            stringResource(R.string.desc_speakerphone_off)
                        }
                    )
                }
                CallUiButton(
                    iconResourceId = R.drawable.ic_volume_up_white_24dp,
                    onClick = onVolume,
                    contentDescription = stringResource(R.string.action_volume)
                )
            }

            Button(
                modifier = Modifier
                    .requiredSize(40.dp)
                    .align(Alignment.CenterHorizontally),
                colors = ButtonDefaults.primaryButtonColors(
                    backgroundColor = colorResource(id = android.R.color.holo_red_dark),
                    contentColor = Color.White
                ),
                onClick = onEndCall
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_call_end_24dp),
                    contentDescription = stringResource(id = R.string.action_hangup)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun CallUiButton(
    modifier: Modifier = Modifier,
    isChecked: Boolean = false,
    @DrawableRes iconResourceId: Int,
    contentDescription: String?,
    onClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .requiredSizeIn(40.dp, 40.dp)
            .clickable(
                onClick = onClick,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(
                    color = MaterialTheme.colors.onSurface,
                    radius = 20.dp
                )
            )
            .border(
                width = 1.dp,
                brush = SolidColor(if (isChecked) Color.White else Color.Transparent),
                shape = MaterialTheme.shapes.small
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = 12.dp
            )
        ) {
            Icon(
                modifier = Modifier.requiredSize(24.dp),
                painter = painterResource(id = iconResourceId),
                contentDescription = contentDescription
            )
        }
    }
}

@WearPreviewDevices
@Composable
private fun NoCallActiveScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
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
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val isRound = LocalConfiguration.current.isScreenRound
    val screenHeightDp = LocalConfiguration.current.screenHeightDp

    var keypadText by remember { mutableStateOf("") }
    val digits by remember {
        derivedStateOf { listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '*', '0', '#') }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.2f)
                .background(Color(0xFF444444))
                .padding(
                    start = if (isRound) 48.dp else 8.dp,
                    end = if (isRound) 48.dp else 8.dp,
                    bottom = 4.dp
                )
                .clipToBounds(),
            contentAlignment = Alignment.BottomCenter
        ) {
            Text(
                modifier = Modifier.wrapContentWidth(
                    align = Alignment.End,
                    unbounded = true
                ),
                text = if (isPreview) "01234567891110" else keypadText,
                fontWeight = FontWeight.Light,
                fontSize = 18.sp,
                maxLines = 1,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Visible
            )
        }
        BoxWithConstraints {
            FlowRow(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        start = if (isRound) 32.dp else 8.dp,
                        end = if (isRound) 32.dp else 8.dp,
                        bottom = if (isRound) 32.dp else 8.dp
                    ),
                maxItemsInEachRow = 3,
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
                overflow = FlowRowOverflow.Visible
            ) {
                digits.forEach {
                    Box(
                        modifier = Modifier
                            .weight(1f, fill = true)
                            .height((this@BoxWithConstraints.maxHeight - if (isRound) 32.dp else 8.dp) / 4)
                            .clickable {
                                keypadText += it
                                onKeyPressed.invoke(it)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = it + "",
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            fontSize = 16.sp
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
            callerBitmap = bmp,
            isSpeakerPhoneOn = true,
            isCallActive = true,
            isMuted = true,
            supportsSpeaker = true,
            canSendDTMFKeys = true
        )
    }

    CallManagerUi(uiState = uiState)
}