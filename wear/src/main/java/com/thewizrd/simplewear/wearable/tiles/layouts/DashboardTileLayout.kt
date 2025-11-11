@file:OptIn(ProtoLayoutExperimental::class)
@file:kotlin.OptIn(ExperimentalHorologistApi::class)
@file:Suppress("FunctionName")

package com.thewizrd.simplewear.wearable.tiles.layouts

import android.content.Context
import androidx.annotation.OptIn
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FONT_VARIANT_BODY
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.SpanImage
import androidx.wear.protolayout.LayoutElementBuilders.SpanText
import androidx.wear.protolayout.LayoutElementBuilders.Spannable
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_MARQUEE
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.ButtonGroupDefaults.DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS
import androidx.wear.protolayout.material3.CardDefaults.filledTonalCardColors
import androidx.wear.protolayout.material3.DataCardStyle
import androidx.wear.protolayout.material3.MaterialScope
import androidx.wear.protolayout.material3.Typography.BODY_MEDIUM
import androidx.wear.protolayout.material3.buttonGroup
import androidx.wear.protolayout.material3.icon
import androidx.wear.protolayout.material3.iconButton
import androidx.wear.protolayout.material3.iconEdgeButton
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textDataCard
import androidx.wear.protolayout.modifiers.LayoutModifier
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.contentDescription
import androidx.wear.protolayout.types.LayoutColor
import androidx.wear.protolayout.types.layoutString
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.tools.tileRendererPreviewData
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.ui.theme.wearTileColorScheme
import com.thewizrd.simplewear.ui.tiles.tools.WearPreviewDevices
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BATTERY
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BATTERY_CHARGING
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BATTERY_SAVER
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BT_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BT_ON
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DATA_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DATA_ON
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DND_ALARMS
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DND_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DND_PRIORITY
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_DND_SILENCE
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_FLASHLIGHT
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_HOTSPOT
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_LOCATION_BATSAVER
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_LOCATION_HIGHACC
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_LOCATION_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_LOCATION_SENSORSONLY
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_LOCK
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_NFC_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_NFC_ON
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_PHONEDISCONNECTED
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_SILENT
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_SOUND
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_VIB
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_WIFI_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_WIFI_ON
import com.thewizrd.simplewear.wearable.tiles.DashboardTileState
import java.util.Locale

internal fun DashboardTileLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: DashboardTileState
): LayoutElement =
    materialScope(context, deviceParameters, defaultColorScheme = wearTileColorScheme) {
        if (state.connectionStatus != WearConnectionStatus.CONNECTED) {
            when (state.connectionStatus) {
                WearConnectionStatus.APPNOTINSTALLED -> {
                    primaryLayout(
                        titleSlot = {
                            text(text = context.getString(R.string.title_activity_dashboard).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = DashboardTileRenderer.getTapAction(
                                        context
                                    )
                                ),
                                width = expand(),
                                height = expand(),
                                title = {
                                    text(
                                        text = context.getString(R.string.error_notinstalled).layoutString,
                                        typography = BODY_MEDIUM,
                                        maxLines = 3
                                    )
                                },
                                colors = filledTonalCardColors(),
                                style = DataCardStyle.smallDataCardStyle()
                            )
                        },
                        bottomSlot = {
                            iconEdgeButton(
                                modifier = LayoutModifier.contentDescription(context.getString(R.string.common_open_on_phone)),
                                onClick = clickable(id = ID_OPENONPHONE),
                                iconContent = {
                                    icon(ID_OPENONPHONE)
                                }
                            )
                        }
                    )
                }

                else -> {
                    primaryLayout(
                        titleSlot = {
                            text(text = context.getString(R.string.title_activity_dashboard).layoutString)
                        },
                        mainSlot = {
                            textDataCard(
                                onClick = clickable(
                                    action = DashboardTileRenderer.getTapAction(
                                        context
                                    )
                                ),
                                width = expand(),
                                height = expand(),
                                title = {
                                    text(
                                        text = context.getString(R.string.status_disconnected).layoutString,
                                        typography = BODY_MEDIUM,
                                        maxLines = 3
                                    )
                                },
                                colors = filledTonalCardColors(),
                                style = DataCardStyle.smallDataCardStyle()
                            )
                        },
                        bottomSlot = {
                            iconEdgeButton(
                                modifier = LayoutModifier.contentDescription(context.getString(R.string.status_disconnected)),
                                onClick = clickable(id = ID_PHONEDISCONNECTED),
                                iconContent = {
                                    icon(ID_PHONEDISCONNECTED)
                                }
                            )
                        }
                    )
                }
            }
    } else {
            primaryLayout(
                titleSlot = state.takeIf { it.showBatteryStatus }?.let { state ->
                    {
                    Spannable.Builder()
                        .addSpan(
                            SpanImage.Builder()
                                .setResourceId(
                                    state.batteryStatus?.let { status ->
                                        if (status.isCharging) ID_BATTERY_CHARGING else ID_BATTERY
                                    } ?: ID_BATTERY
                                )
                                .setWidth(dp(16f))
                                .setHeight(dp(16f))
                                .build()
                        )
                        .addSpan(
                            SpanText.Builder()
                                .setText(
                                    state.batteryStatus?.let { status ->
                                        String.format(
                                            Locale.ROOT,
                                            "%d%%, %s",
                                            status.batteryLevel,
                                            if (status.isCharging) {
                                                context.getString(R.string.batt_state_charging)
                                            } else context.getString(
                                                R.string.batt_state_discharging
                                            )
                                        )
                                    } ?: context.getString(R.string.state_unknown)
                                )
                                .setFontStyle(
                                    FontStyle.Builder()
                                        .setSize(sp(12f))
                                        .setWeight(FONT_WEIGHT_MEDIUM)
                                        .setVariant(FONT_VARIANT_BODY)
                                        .build()
                                )
                                .build()
                        )
                        .setMaxLines(1)
                        .setMultilineAlignment(HORIZONTAL_ALIGN_CENTER)
                        .setOverflow(TEXT_OVERFLOW_MARQUEE)
                        .build()
                }
                },
                mainSlot = {
                    Column.Builder()
                        .setWidth(expand())
                        .setHeight(expand())
                    .apply {
                        val chunks = state.actions.toList().chunked(3)
                        chunks.forEachIndexed { index, chunk ->
                            addContent(
                                buttonGroup {
                                    chunk.forEach { (actionType, _) ->
                                        buttonGroupItem {
                                            ActionButton(state, actionType)
                                        }
                                    }
                                }
                            )

                            if (index < chunks.size - 1) {
                                addContent(DEFAULT_SPACER_BETWEEN_BUTTON_GROUPS)
                            }
                        }
                    }
                        .build()
                }
            )
        }
    }

private fun MaterialScope.ActionButton(
    state: DashboardTileState,
    action: Actions
): LayoutElement {
    val isEnabled = state.isActionEnabled(action)

    return iconButton(
        onClick = clickable(
            id = action.name,
            action = ActionBuilders.LoadAction.Builder()
                .setRequestState(
                    StateBuilders.State.Builder()
                        .addKeyToValueMapping(
                            AppDataKey(action.name),
                            DynamicDataBuilders.DynamicDataValue.fromBool(
                                state.isNextActionEnabled(action)
                            )
                        )
                        .build()
                )
                .build()
        ),
        width = expand(),
        height = expand(),
        iconContent = {
            icon(protoLayoutResourceId = getResourceIdForAction(state, action))
        },
        colors = filledTonalButtonColors().copy(
            containerColor = LayoutColor(
                staticArgb = if (isEnabled) {
                    colorScheme.primaryContainer.staticArgb
                } else {
                    colorScheme.surfaceContainer.staticArgb
                },
                dynamicArgb = DynamicBuilders.DynamicColor
                    .onCondition(
                        DynamicBuilders.DynamicBool.from(AppDataKey(action.name))
                    )
                    .use(colorScheme.primaryContainer.staticArgb)
                    .elseUse(colorScheme.surfaceContainer.staticArgb)
                    .animate()
            ),
            iconColor = LayoutColor(
                staticArgb = if (isEnabled) {
                    colorScheme.onPrimaryContainer.staticArgb
                } else {
                    colorScheme.onSurface.staticArgb
                },
                dynamicArgb = DynamicBuilders.DynamicColor
                    .onCondition(
                        DynamicBuilders.DynamicBool.from(AppDataKey(action.name))
                    )
                    .use(colorScheme.onPrimaryContainer.staticArgb)
                    .elseUse(colorScheme.onSurface.staticArgb)
                    .animate()
            )
        )
    )
}

private fun getResourceIdForAction(state: DashboardTileState, action: Actions): String {
    return when (action) {
        Actions.WIFI -> {
            if ((state.getAction(action) as? ToggleAction)?.isEnabled == true) ID_WIFI_ON else ID_WIFI_OFF
        }

        Actions.BLUETOOTH -> {
            if ((state.getAction(action) as? ToggleAction)?.isEnabled == true) ID_BT_ON else ID_BT_OFF
        }

        Actions.MOBILEDATA -> {
            if ((state.getAction(action) as? ToggleAction)?.isEnabled == true) ID_DATA_ON else ID_DATA_OFF
        }

        Actions.LOCATION -> {
            val locationAction = state.getAction(action)

            val locChoice = if (locationAction is ToggleAction) {
                if (locationAction.isEnabled) LocationState.HIGH_ACCURACY else LocationState.OFF
            } else if (locationAction is MultiChoiceAction) {
                LocationState.valueOf(locationAction.choice)
            } else {
                LocationState.OFF
            }

            when (locChoice) {
                LocationState.OFF -> ID_LOCATION_OFF
                LocationState.SENSORS_ONLY -> ID_LOCATION_SENSORSONLY
                LocationState.BATTERY_SAVING -> ID_LOCATION_BATSAVER
                LocationState.HIGH_ACCURACY -> ID_LOCATION_HIGHACC
            }
        }

        Actions.TORCH -> ID_FLASHLIGHT
        Actions.LOCKSCREEN -> ID_LOCK
        Actions.DONOTDISTURB -> {
            val dndAction = state.getAction(action)

            val dndChoice = if (dndAction is ToggleAction) {
                if (dndAction.isEnabled) DNDChoice.PRIORITY else DNDChoice.OFF
            } else if (dndAction is MultiChoiceAction) {
                DNDChoice.valueOf(dndAction.choice)
            } else {
                DNDChoice.OFF
            }

            when (dndChoice) {
                DNDChoice.OFF -> ID_DND_OFF
                DNDChoice.PRIORITY -> ID_DND_PRIORITY
                DNDChoice.ALARMS -> ID_DND_ALARMS
                DNDChoice.SILENCE -> ID_DND_SILENCE
            }
        }

        Actions.RINGER -> {
            val ringerAction = state.getAction(action) as? MultiChoiceAction
            val ringerChoice = ringerAction?.choice?.let {
                RingerChoice.valueOf(it)
            } ?: RingerChoice.VIBRATION

            when (ringerChoice) {
                RingerChoice.VIBRATION -> ID_RINGER_VIB
                RingerChoice.SOUND -> ID_RINGER_SOUND
                RingerChoice.SILENT -> ID_RINGER_SILENT
            }
        }

        Actions.HOTSPOT -> ID_HOTSPOT

        Actions.NFC -> {
            if ((state.getAction(action) as? ToggleAction)?.isEnabled == true) ID_NFC_ON else ID_NFC_OFF
        }

        Actions.BATTERYSAVER -> ID_BATTERY_SAVER

        else -> ""
    }
}

@WearPreviewDevices
private fun DashboardTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)

@WearPreviewDevices
private fun DashboardLoadingTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.CONNECTED,
        batteryStatus = null,
        actions = emptyMap()
    ),
    resourceState = Unit
)

@WearPreviewDevices
private fun DashboardDisconnectTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.DISCONNECTED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)

@WearPreviewDevices
private fun DashboardNotInstalledTilePreview(context: Context) = tileRendererPreviewData(
    renderer = DashboardTileRenderer(context, debugResourceMode = true),
    tileState = DashboardTileState(
        connectionStatus = WearConnectionStatus.APPNOTINSTALLED,
        batteryStatus = BatteryStatus(100, true),
        actions = mapOf(
            Actions.WIFI to ToggleAction(Actions.WIFI, true),
            Actions.BLUETOOTH to ToggleAction(Actions.BLUETOOTH, true),
            Actions.LOCKSCREEN to NormalAction(Actions.LOCKSCREEN),
            Actions.DONOTDISTURB to MultiChoiceAction(
                Actions.DONOTDISTURB,
                DNDChoice.OFF.value
            ),
            Actions.RINGER to MultiChoiceAction(Actions.RINGER, RingerChoice.VIBRATION.value),
            Actions.TORCH to NormalAction(Actions.TORCH)
        )
    ),
    resourceState = Unit
)