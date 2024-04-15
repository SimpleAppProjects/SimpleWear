package com.thewizrd.simplewear.wearable.tiles.layouts

import android.content.Context
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders.FONT_VARIANT_BODY
import androidx.wear.protolayout.LayoutElementBuilders.FONT_WEIGHT_MEDIUM
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.SpanImage
import androidx.wear.protolayout.LayoutElementBuilders.SpanText
import androidx.wear.protolayout.LayoutElementBuilders.Spannable
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_ALIGN_CENTER
import androidx.wear.protolayout.LayoutElementBuilders.TEXT_OVERFLOW_MARQUEE
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.protolayout.expression.DynamicDataBuilders
import androidx.wear.protolayout.expression.ProtoLayoutExperimental
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonColors
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.MultiButtonLayout
import androidx.wear.protolayout.material.layouts.MultiButtonLayout.FIVE_BUTTON_DISTRIBUTION_TOP_HEAVY
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.DNDChoice
import com.thewizrd.shared_resources.actions.LocationState
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.RingerChoice
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.helpers.WearConnectionStatus
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_BATTERY
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
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_OPENONPHONE
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_PHONEDISCONNECTED
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_SILENT
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_SOUND
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_RINGER_VIB
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_WIFI_OFF
import com.thewizrd.simplewear.wearable.tiles.DashboardTileRenderer.Companion.ID_WIFI_ON
import com.thewizrd.simplewear.wearable.tiles.DashboardTileState
import java.util.Locale

private val CIRCLE_SIZE = dp(48f)
private val SMALL_CIRCLE_SIZE = dp(40f)

private val ICON_SIZE = dp(24f)
private val SMALL_ICON_SIZE = dp(20f)

private val COLORS = Colors(
    0xff91cfff.toInt(), 0xff000000.toInt(),
    0xff202124.toInt(), 0xffffffff.toInt()
)

@OptIn(ProtoLayoutExperimental::class)
internal fun DashboardTileLayout(
    context: Context,
    deviceParameters: DeviceParameters,
    state: DashboardTileState
): LayoutElement {
    return if (state.connectionStatus != WearConnectionStatus.CONNECTED) {
        PrimaryLayout.Builder(deviceParameters)
            .apply {
                when (state.connectionStatus) {
                    WearConnectionStatus.APPNOTINSTALLED -> {
                        setContent(
                            Text.Builder(context, context.getString(R.string.error_notinstalled))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .setColor(
                                    ColorBuilders.argb(
                                        ContextCompat.getColor(context, R.color.colorSecondary)
                                    )
                                )
                                .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                .setMaxLines(3)
                                .build()
                        )

                        setPrimaryChipContent(
                            IconButton(
                                context,
                                ID_OPENONPHONE,
                                context.getString(R.string.common_open_on_phone),
                                Clickable.Builder()
                                    .setId(ID_OPENONPHONE)
                                    .setOnClick(
                                        ActionBuilders.LoadAction.Builder()
                                            .build()
                                    )
                                    .build(),
                                size = SMALL_CIRCLE_SIZE,
                                iconSize = SMALL_ICON_SIZE
                            )
                        )
                    }

                    else -> {
                        setContent(
                            Text.Builder(context, context.getString(R.string.status_disconnected))
                                .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                                .setColor(
                                    ColorBuilders.argb(
                                        ContextCompat.getColor(context, R.color.colorSecondary)
                                    )
                                )
                                .setMultilineAlignment(TEXT_ALIGN_CENTER)
                                .setMaxLines(3)
                                .build()
                        )

                        setPrimaryChipContent(
                            IconButton(
                                context,
                                resourceId = ID_PHONEDISCONNECTED,
                                contentDescription = context.getString(R.string.status_disconnected),
                                size = SMALL_CIRCLE_SIZE,
                                iconSize = SMALL_ICON_SIZE
                            )
                        )
                    }
                }
            }
            .build()
    } else {
        return PrimaryLayout.Builder(deviceParameters)
            .setPrimaryLabelTextContent(
                if (state.showBatteryStatus) {
                    Spannable.Builder()
                        .addSpan(
                            SpanImage.Builder()
                                .setResourceId(ID_BATTERY)
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
                } else {
                    Spacer.Builder().build()
                }
            )
            .setContent(
                MultiButtonLayout.Builder()
                    .setFiveButtonDistribution(FIVE_BUTTON_DISTRIBUTION_TOP_HEAVY)
                    .apply {
                        state.actions.forEach { (actionType, _) ->
                            addButtonContent(
                                ActionButton(context, deviceParameters, state, actionType)
                            )
                        }
                    }
                    .build()
            )
            .build()
    }
}

private fun IconButton(
    context: Context,
    resourceId: String,
    contentDescription: String = "",
    clickable: Clickable = Clickable.Builder().build(),
    size: DimensionBuilders.DpProp? = CIRCLE_SIZE,
    iconSize: DimensionBuilders.DpProp = ICON_SIZE
) = Button.Builder(context, clickable)
    .setContentDescription(contentDescription)
    .setButtonColors(ButtonColors.primaryButtonColors(COLORS))
    .setIconContent(resourceId, iconSize)
    .apply {
        if (size != null) {
            setSize(size)
        }
    }
    .build()

private fun ActionButton(
    context: Context,
    deviceParameters: DeviceParameters,
    state: DashboardTileState,
    action: Actions
) = Button.Builder(
    context,
    Clickable.Builder()
        .setId(action.name)
        .setOnClick(
            ActionBuilders.LoadAction.Builder()
                .setRequestState(
                    StateBuilders.State.Builder()
                        .addKeyToValueMapping(
                            AppDataKey(action.name),
                            DynamicDataBuilders.DynamicDataValue.fromBool(
                                state.isNextActionEnabled(
                                    action
                                )
                            )
                        )
                        .build()
                )
                .build()
        )
        .build()
)
    .setButtonColors(
        ButtonColors(
            ColorBuilders.ColorProp.Builder(
                ContextCompat.getColor(context, R.color.buttonDisabled)
            )
                .setDynamicValue(
                    DynamicBuilders.DynamicColor
                        .onCondition(
                            DynamicBuilders.DynamicBool.from(AppDataKey(action.name))
                        )
                        .use(ContextCompat.getColor(context, R.color.colorPrimary))
                        .elseUse(ContextCompat.getColor(context, R.color.buttonDisabled))
                        .animate()
                )
                .build(),
            ColorBuilders.argb(Color.WHITE)
        )
    )
    .apply {
        val isSmol =
            minOf(deviceParameters.screenHeightDp, deviceParameters.screenWidthDp) <= 192f
        setIconContent(
            getResourceIdForAction(state, action),
            if (isSmol) SMALL_ICON_SIZE else ICON_SIZE
        )
        setSize(if (isSmol) SMALL_CIRCLE_SIZE else CIRCLE_SIZE)
    }
    .build()

private fun CompactChipButton(
    context: Context,
    deviceParameters: DeviceParameters,
    text: String,
    iconResourceId: String? = null,
    clickable: Clickable = Clickable.Builder().build()
) = CompactChip.Builder(
    context, text, clickable, deviceParameters
).setChipColors(
    ChipColors.primaryChipColors(COLORS)
).apply {
    if (iconResourceId != null) {
        setIconContent(iconResourceId)
    }
}.build()

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
        else -> ""
    }
}

fun DashboardTileState.isActionEnabled(action: Actions): Boolean {
    return when (action) {
        Actions.WIFI, Actions.BLUETOOTH, Actions.MOBILEDATA, Actions.TORCH, Actions.HOTSPOT -> {
            (getAction(action) as? ToggleAction)?.isEnabled == true
        }

        Actions.LOCATION -> {
            val locationAction = getAction(action)

            val locChoice = if (locationAction is ToggleAction) {
                if (locationAction.isEnabled) LocationState.HIGH_ACCURACY else LocationState.OFF
            } else if (locationAction is MultiChoiceAction) {
                LocationState.valueOf(locationAction.choice)
            } else {
                LocationState.OFF
            }

            locChoice != LocationState.OFF
        }

        Actions.LOCKSCREEN -> true
        Actions.DONOTDISTURB -> {
            val dndAction = getAction(action)

            val dndChoice = if (dndAction is ToggleAction) {
                if (dndAction.isEnabled) DNDChoice.PRIORITY else DNDChoice.OFF
            } else if (dndAction is MultiChoiceAction) {
                DNDChoice.valueOf(dndAction.choice)
            } else {
                DNDChoice.OFF
            }

            dndChoice != DNDChoice.OFF
        }

        Actions.RINGER -> {
            val ringerAction = getAction(action) as? MultiChoiceAction
            val ringerChoice = ringerAction?.choice?.let {
                RingerChoice.valueOf(it)
            } ?: RingerChoice.VIBRATION

            ringerChoice != RingerChoice.SILENT
        }

        else -> false
    }
}

fun DashboardTileState.isNextActionEnabled(action: Actions): Boolean {
    val actionState = getAction(action)

    if (actionState == null) {
        return when (action) {
            // Normal actions
            Actions.LOCKSCREEN -> true
            // others
            else -> false
        }
    } else {
        return when (actionState) {
            is ToggleAction -> {
                !actionState.isEnabled
            }

            is MultiChoiceAction -> {
                val newChoice = actionState.choice + 1
                val ma = MultiChoiceAction(action, newChoice)
                ma.choice > 0
            }

            is NormalAction -> {
                true
            }

            else -> {
                false
            }
        }
    }
}