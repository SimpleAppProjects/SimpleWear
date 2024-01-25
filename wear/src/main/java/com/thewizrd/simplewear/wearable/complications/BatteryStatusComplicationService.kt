package com.thewizrd.simplewear.wearable.complications

import android.app.PendingIntent
import android.content.ComponentName
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
import com.thewizrd.simplewear.DashboardActivity
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.wearable.tiles.DashboardTileMessenger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel

class BatteryStatusComplicationService : SuspendingComplicationDataSourceService() {
    companion object {
        private const val TAG = "BatteryStatusComplicationService"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val tileMessenger = DashboardTileMessenger(this)

    private val supportedComplicationTypes: Set<ComplicationType> =
        setOf(
            ComplicationType.RANGED_VALUE,
            ComplicationType.SHORT_TEXT,
            ComplicationType.LONG_TEXT
        )
    private val complicationIconResId = R.drawable.ic_smartphone_white_24dp

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onComplicationActivated(complicationInstanceId: Int, type: ComplicationType) {
        super.onComplicationActivated(complicationInstanceId, type)

        ComplicationDataSourceUpdateRequester.create(
            applicationContext,
            ComponentName(applicationContext, this::class.java)
        ).run {
            requestUpdate(complicationInstanceId)
        }
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData {
        if (!supportedComplicationTypes.contains(request.complicationType)) {
            return NoDataComplicationData()
        }

        return scope.async {
            val batteryStatus = tileMessenger.requestBatteryStatusAsync()
                ?: return@async NoDataComplicationData()

            val batteryLvl = batteryStatus.batteryLevel
            val statusText = if (batteryStatus.isCharging) {
                getString(R.string.batt_state_charging)
            } else {
                getString(R.string.batt_state_discharging)
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
                        PlainComplicationText.Builder("70%").build(),
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
                        PlainComplicationText.Builder("70%, $statusText").build(),
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
        val onClickIntent = Intent(applicationContext, DashboardActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        return PendingIntent.getActivity(
            applicationContext,
            0,
            onClickIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}