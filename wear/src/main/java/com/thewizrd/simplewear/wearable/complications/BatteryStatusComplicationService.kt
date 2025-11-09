package com.thewizrd.simplewear.wearable.complications

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.NoDataComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.thewizrd.shared_resources.actions.BatteryStatus
import com.thewizrd.shared_resources.appLib
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.DashboardActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.datastore.dashboard.dashboardDataStore
import com.thewizrd.simplewear.utils.asLauncherIntent
import com.thewizrd.simplewear.wearable.tiles.DashboardTileMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BatteryStatusComplicationService : SuspendingComplicationDataSourceService() {
    companion object {
        private const val TAG = "BatteryStatusComplicationService"

        fun requestComplicationUpdate(context: Context, complicationInstanceId: Int? = null) {
            updateJob?.cancel()

            updateJob = appLib.appScope.launch {
                delay(1000)
                if (isActive) {
                    Logger.debug(TAG, "requesting complication update")

                    ComplicationDataSourceUpdateRequester.create(
                        context,
                        ComponentName(context, this::class.java)
                    ).run {
                        if (complicationInstanceId != null) {
                            requestUpdate(complicationInstanceId)
                        } else {
                            requestUpdateAll()
                        }
                    }
                }
            }
        }

        private var updateJob: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tileMessenger = DashboardTileMessenger(this)

    private val supportedComplicationTypes: Set<ComplicationType> =
        setOf(
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT
        )

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)
        requestComplicationUpdate(applicationContext, complicationInstanceId)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!supportedComplicationTypes.contains(request.complicationType)) {
            return NoDataComplicationData()
        }

        return scope.async {
            val batteryStatus = latestStatus() ?: return@async NoDataComplicationData()

            val batteryLvl = batteryStatus.batteryLevel.coerceIn(0, 100)
            val statusText = if (batteryStatus.isCharging) {
                getString(R.string.batt_state_charging)
            } else {
                getString(R.string.batt_state_discharging)
            }
            val complicationIconResId = if (batteryStatus.isCharging) {
                R.drawable.ic_charging_station_24dp
            } else {
                R.drawable.ic_smartphone_white_24dp
            }

            when (request.complicationType) {
                ComplicationType.RANGED_VALUE -> {
                    RangedValueComplicationData.Builder(
                        batteryLvl.toFloat(), 0f, 100f,
                        PlainComplicationText.Builder("${getString(R.string.pref_title_phone_batt_state)}: ${batteryLvl}%, $statusText")
                            .build()
                    ).setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(applicationContext, complicationIconResId)
                        ).build()
                    ).setText(
                        PlainComplicationText.Builder("$batteryLvl").build()
                    ).setTapAction(
                        getTapIntent()
                    ).build()
                }

                ComplicationType.SHORT_TEXT -> {
                    ShortTextComplicationData.Builder(
                        PlainComplicationText.Builder("${batteryLvl}%").build(),
                        PlainComplicationText.Builder("${getString(R.string.pref_title_phone_batt_state)}: ${batteryLvl}%, $statusText")
                            .build()
                    ).setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(applicationContext, complicationIconResId)
                        ).build()
                    ).setTapAction(
                        getTapIntent()
                    ).build()
                }

                ComplicationType.LONG_TEXT -> {
                    LongTextComplicationData.Builder(
                        PlainComplicationText.Builder("${batteryLvl}%, $statusText").build(),
                        PlainComplicationText.Builder("${getString(R.string.pref_title_phone_batt_state)}: ${batteryLvl}%, $statusText")
                            .build()
                    ).setTitle(
                        PlainComplicationText.Builder(getString(R.string.pref_title_phone_batt_state))
                            .build()
                    ).setMonochromaticImage(
                        MonochromaticImage.Builder(
                            Icon.createWithResource(applicationContext, complicationIconResId)
                        ).build()
                    ).setTapAction(
                        getTapIntent()
                    ).build()
                }

                else -> NoDataComplicationData()
            }
        }.await()
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData {
        if (!supportedComplicationTypes.contains(type)) {
            return NoDataComplicationData()
        }

        val complicationIconResId = R.drawable.ic_charging_station_24dp

        return when (type) {
            ComplicationType.RANGED_VALUE -> {
                RangedValueComplicationData.Builder(
                    70f, 0f, 100f,
                    PlainComplicationText.Builder("Phone Battery Status: 70%, Charging").build()
                ).setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(applicationContext, complicationIconResId)
                    ).build()
                ).setText(
                    PlainComplicationText.Builder("70").build()
                ).build()
            }

            ComplicationType.SHORT_TEXT -> {
                ShortTextComplicationData.Builder(
                    PlainComplicationText.Builder("70%").build(),
                    PlainComplicationText.Builder("Phone Battery Status: 70%, Charging").build()
                ).setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(applicationContext, complicationIconResId)
                    ).build()
                ).build()
            }

            ComplicationType.LONG_TEXT -> {
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder("70%, Charging").build(),
                    PlainComplicationText.Builder("Phone Battery Status: 70%, Charging").build()
                ).setTitle(
                    PlainComplicationText.Builder("Phone Battery Status").build()
                ).setMonochromaticImage(
                    MonochromaticImage.Builder(
                        Icon.createWithResource(applicationContext, complicationIconResId)
                    ).build()
                ).build()
            }

            else -> {
                NoDataComplicationData()
            }
        }
    }

    override fun onComplicationDeactivated(complicationInstanceId: Int) {
        super.onComplicationDeactivated(complicationInstanceId)
    }

    private fun getTapIntent(): PendingIntent {
        return with(
            Intent(applicationContext, DashboardActivity::class.java).asLauncherIntent()
        ) {
            PendingIntent.getActivity(
                applicationContext,
                0,
                this,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private suspend fun latestStatus(): BatteryStatus? {
        var status = this.dashboardDataStore.data.map { it.batteryStatus }.firstOrNull()

        if (status == null) {
            Logger.debug(TAG, "No battery status available. loading from remote...")
            status = tileMessenger.requestBatteryStatusAsync()
        }

        return status
    }
}