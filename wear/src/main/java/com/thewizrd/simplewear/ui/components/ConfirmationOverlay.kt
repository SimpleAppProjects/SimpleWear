package com.thewizrd.simplewear.ui.components

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.material3.ConfirmationDialog
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.FailureConfirmationDialog
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.Text
import com.thewizrd.simplewear.viewmodels.ConfirmationData
import com.thewizrd.simplewear.viewmodels.ConfirmationType
import androidx.wear.compose.material3.R as wearM3Res
import com.thewizrd.shared_resources.R as sharedRes

@Composable
fun ConfirmationOverlay(
    confirmationData: ConfirmationData?,
    onTimeout: () -> Unit,
    showDialog: Boolean = confirmationData != null
) {
    when (confirmationData?.confirmationType) {
        ConfirmationType.Success -> {
            if (confirmationData.messageResId != null) {
                ConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    colors = ConfirmationDialogDefaults.successColors(),
                    text = {
                        Text(text = stringResource(confirmationData.messageResId))
                    },
                    content = {
                        ConfirmationDialogDefaults.SuccessIcon(
                            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize)
                        )
                    }
                )
            } else if (confirmationData.message != null) {
                ConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    colors = ConfirmationDialogDefaults.successColors(),
                    text = {
                        Text(text = confirmationData.message)
                    },
                    content = {
                        ConfirmationDialogDefaults.SuccessIcon(
                            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize)
                        )
                    }
                )
            } else {
                SuccessConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    curvedText = null
                )
            }
        }

        ConfirmationType.Failure -> {
            if (confirmationData.messageResId != null) {
                ConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    colors = ConfirmationDialogDefaults.failureColors(),
                    text = {
                        Text(text = stringResource(confirmationData.messageResId))
                    },
                    content = {
                        ConfirmationDialogDefaults.FailureIcon(
                            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize)
                        )
                    }
                )
            } else if (confirmationData.message != null) {
                ConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    colors = ConfirmationDialogDefaults.failureColors(),
                    text = {
                        Text(text = confirmationData.message)
                    },
                    content = {
                        ConfirmationDialogDefaults.FailureIcon(
                            modifier = Modifier.size(ConfirmationDialogDefaults.SmallIconSize)
                        )
                    }
                )
            } else {
                FailureConfirmationDialog(
                    visible = showDialog,
                    onDismissRequest = onTimeout,
                    curvedText = null
                )
            }
        }

        ConfirmationType.OpenOnPhone -> {
            ConfirmationDialog(
                visible = showDialog,
                onDismissRequest = onTimeout,
                colors = ConfirmationDialogDefaults.colors(),
                text = confirmationData.messageResId?.let {
                    {
                        Text(text = stringResource(it))
                    }
                } ?: confirmationData.message?.let {
                    {
                        Text(text = it)
                    }
                },
                content = {
                    val image =
                        AnimatedImageVector.animatedVectorResource(wearM3Res.drawable.wear_m3c_open_on_phone_animation)
                    var atEnd by remember { mutableStateOf(false) }

                    Icon(
                        modifier = Modifier.size(
                            if (confirmationData.messageResId != null || confirmationData.message != null) {
                                ConfirmationDialogDefaults.SmallIconSize
                            } else {
                                ConfirmationDialogDefaults.IconSize
                            }
                        ),
                        painter = rememberAnimatedVectorPainter(image, atEnd),
                        contentDescription = stringResource(wearM3Res.string.wear_m3c_open_on_phone_icon_content_description)
                    )

                    LaunchedEffect(Unit) {
                        atEnd = !atEnd
                    }
                }
            )
        }

        else -> {
            ConfirmationDialog(
                visible = showDialog,
                onDismissRequest = onTimeout,
                colors = ConfirmationDialogDefaults.colors(),
                text = confirmationData?.messageResId?.let {
                    {
                        Text(text = stringResource(it))
                    }
                } ?: confirmationData?.message?.let {
                    {
                        Text(text = it)
                    }
                },
                content = confirmationData?.animatedVectorResId?.let { iconResId ->
                    {
                        val image = AnimatedImageVector.animatedVectorResource(iconResId)
                        var atEnd by remember { mutableStateOf(false) }

                        Icon(
                            modifier = Modifier.size(
                                if (confirmationData.messageResId != null || confirmationData.message != null) {
                                    ConfirmationDialogDefaults.SmallIconSize
                                } else {
                                    ConfirmationDialogDefaults.IconSize
                                }
                            ),
                            painter = rememberAnimatedVectorPainter(image, atEnd),
                            contentDescription = null
                        )

                        LaunchedEffect(iconResId) {
                            atEnd = !atEnd
                        }
                    }
                } ?: confirmationData?.iconResId?.let { iconResId ->
                    {
                        Icon(
                            modifier = Modifier.size(
                                if (confirmationData.messageResId != null || confirmationData.message != null) {
                                    ConfirmationDialogDefaults.SmallIconSize
                                } else {
                                    ConfirmationDialogDefaults.IconSize
                                }
                            ),
                            painter = painterResource(iconResId),
                            contentDescription = null
                        )
                    }
                } ?: {
                    Icon(
                        modifier = Modifier.size(
                            if (confirmationData?.messageResId != null || confirmationData?.message != null) {
                                ConfirmationDialogDefaults.SmallIconSize
                            } else {
                                ConfirmationDialogDefaults.IconSize
                            }
                        ),
                        painter = painterResource(sharedRes.drawable.ic_check_white_24dp),
                        contentDescription = null
                    )
                }
            )
        }
    }
}