package com.thewizrd.simplewear.wearable.tiles

import android.content.ComponentName
import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ModifiersBuilders.Clickable
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.StateBuilders
import androidx.wear.protolayout.expression.AppDataKey
import androidx.wear.protolayout.expression.DynamicDataBuilders
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.tiles.images.drawableResToImageResource
import com.google.android.horologist.tiles.render.SingleTileLayoutRendererWithState
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.PhoneSyncActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.layouts.DashboardTileLayout
import com.thewizrd.simplewear.wearable.tiles.layouts.LoadingTileLayout

@OptIn(ExperimentalHorologistApi::class)
class DashboardTileRenderer(context: Context, debugResourceMode: Boolean = false) :
    SingleTileLayoutRendererWithState<DashboardTileState, Unit>(context, debugResourceMode) {
    companion object {
        // Resource identifiers for images
        internal const val ID_OPENONPHONE = "open_on_phone"
        internal const val ID_PHONEDISCONNECTED = "phone_disconn"
        internal const val ID_BATTERY = "batt"
        internal const val ID_BATTERY_CHARGING = "batt_chg"

        // Actions
        // VOLUME, MUSIC, SLEEPTIMER, APPS, PHONE, BRIGHTNESS unavailable
        internal const val ID_WIFI_ON = "wifi_on"
        internal const val ID_WIFI_OFF = "wifi_off"
        internal const val ID_BT_ON = "bt_on"
        internal const val ID_BT_OFF = "bt_off"
        internal const val ID_DATA_ON = "data_on"
        internal const val ID_DATA_OFF = "data_off"
        internal const val ID_LOCATION_OFF = "loc_off"
        internal const val ID_LOCATION_SENSORSONLY = "loc_sensors_only"
        internal const val ID_LOCATION_BATSAVER = "loc_batt_saving"
        internal const val ID_LOCATION_HIGHACC = "loc_high_acc"
        internal const val ID_FLASHLIGHT = "torch"
        internal const val ID_LOCK = "lock"
        internal const val ID_DND_OFF = "dnd_off"
        internal const val ID_DND_PRIORITY = "dnd_priority"
        internal const val ID_DND_ALARMS = "dnd_alarms"
        internal const val ID_DND_SILENCE = "dnd_silence"
        internal const val ID_RINGER_VIB = "ringer_vib"
        internal const val ID_RINGER_SOUND = "ringer_sound"
        internal const val ID_RINGER_SILENT = "ringer_silent"
        internal const val ID_HOTSPOT = "hotspot"
        internal const val ID_NFC_ON = "nfc_on"
        internal const val ID_NFC_OFF = "nfc_off"
        internal const val ID_BATTERY_SAVER = "battery_saver"

        // Background drawables
        internal const val ID_BUTTON_ENABLED = "round_button_enabled"
        internal const val ID_BUTTON_DISABLED = "round_button_disabled"

        fun getTapAction(context: Context): ActionBuilders.Action {
            return ActionBuilders.launchAction(
                ComponentName(context, PhoneSyncActivity::class.java)
            )
        }
    }

    override fun createState(state: DashboardTileState): StateBuilders.State {
        return StateBuilders.State.Builder()
            .apply {
                state.actions.forEach { (actionType, _) ->
                    addKeyToValueMapping(
                        AppDataKey(actionType.name),
                        DynamicDataBuilders.DynamicDataValue.fromBool(
                            state.isActionEnabled(actionType)
                        )
                    )
                }
            }
            .build()
    }

    override fun renderTile(
        state: DashboardTileState,
        deviceParameters: DeviceParametersBuilders.DeviceParameters
    ): LayoutElementBuilders.LayoutElement {
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        Clickable.Builder()
                            .setOnClick(
                                getTapAction(context)
                            )
                            .build()
                    )
                    .build()
            )
            .addContent(
                if (state.isEmpty) {
                    LoadingTileLayout(
                        context,
                        deviceParameters,
                        title = context.getString(R.string.title_activity_dashboard)
                    )
                } else {
                    DashboardTileLayout(context, deviceParameters, state)
                }
            )
            .build()
    }

    override fun ResourceBuilders.Resources.Builder.produceRequestedResources(
        resourceState: Unit,
        deviceParameters: DeviceParametersBuilders.DeviceParameters,
        resourceIds: List<String>
    ) {
        Logger.debug(this::class.java.name, "produceRequestedResources: resIds = $resourceIds")

        val resources = mapOf(
            ID_OPENONPHONE to R.drawable.common_full_open_on_phone,
            ID_PHONEDISCONNECTED to R.drawable.ic_phonelink_erase_white_24dp,
            ID_BATTERY to R.drawable.ic_battery_std_white_24dp,
            ID_BATTERY_CHARGING to R.drawable.ic_battery_charging_white_24dp,

            ID_WIFI_ON to R.drawable.ic_network_wifi_white_24dp,
            ID_WIFI_OFF to R.drawable.ic_signal_wifi_off_white_24dp,

            ID_BT_ON to R.drawable.ic_bluetooth_white_24dp,
            ID_BT_OFF to R.drawable.ic_bluetooth_disabled_white_24dp,

            ID_DATA_ON to R.drawable.ic_network_cell_white_24dp,
            ID_DATA_OFF to R.drawable.ic_signal_cellular_off_white_24dp,

            ID_LOCATION_OFF to R.drawable.ic_location_off_white_24dp,
            ID_LOCATION_SENSORSONLY to R.drawable.ic_baseline_gps_fixed_24dp,
            ID_LOCATION_BATSAVER to R.drawable.ic_outline_location_on_24dp,
            ID_LOCATION_HIGHACC to R.drawable.ic_location_on_white_24dp,

            ID_FLASHLIGHT to R.drawable.ic_lightbulb_outline_white_24dp,

            ID_LOCK to R.drawable.ic_lock_white_24dp,

            ID_DND_OFF to R.drawable.ic_do_not_disturb_off_white_24dp,
            ID_DND_PRIORITY to R.drawable.ic_error_white_24dp,
            ID_DND_ALARMS to R.drawable.ic_alarm_white_24dp,
            ID_DND_SILENCE to R.drawable.ic_do_not_disturb_silence_white_24dp,

            ID_RINGER_VIB to R.drawable.ic_vibration_white_24dp,
            ID_RINGER_SOUND to R.drawable.ic_notifications_active_white_24dp,
            ID_RINGER_SILENT to R.drawable.ic_volume_off_white_24dp,

            ID_HOTSPOT to R.drawable.ic_wifi_tethering,

            ID_NFC_ON to R.drawable.ic_nfc_on,
            ID_NFC_OFF to R.drawable.ic_nfc_off,

            ID_BATTERY_SAVER to R.drawable.ic_battery_saver,

            ID_BUTTON_ENABLED to R.drawable.round_button_enabled,
            ID_BUTTON_DISABLED to R.drawable.round_button_disabled
        )

        (resourceIds.takeIf { it.isNotEmpty() } ?: resources.keys).forEach { key ->
            resources[key]?.let { resId ->
                addIdToImageMapping(key, drawableResToImageResource(resId))
            }
        }
    }
}