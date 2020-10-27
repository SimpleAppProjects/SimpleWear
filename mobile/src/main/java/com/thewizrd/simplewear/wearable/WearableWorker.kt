package com.thewizrd.simplewear.wearable

import android.content.Context
import android.util.Log
import androidx.annotation.StringDef
import androidx.work.*
import com.thewizrd.shared_resources.helpers.Actions
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.stringToBytes
import java.util.*

class WearableWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "WearableWorker"

        // Actions
        private const val KEY_ACTION = "action"
        const val ACTION_SENDSTATUSUPDATE = "SimpleWear.Droid.action.SEND_STATUS_UPDATE"
        const val ACTION_SENDBATTERYUPDATE = "SimpleWear.Droid.action.SEND_BATTERY_UPDATE"
        const val ACTION_SENDWIFIUPDATE = "SimpleWear.Droid.action.SEND_WIFI_UPDATE"
        const val ACTION_SENDBTUPDATE = "SimpleWear.Droid.action.SEND_BT_UPDATE"
        const val ACTION_SENDMOBILEDATAUPDATE = "SimpleWear.Droid.action.SEND_MOBILEDATA_UPDATE"
        const val ACTION_SENDACTIONUPDATE = "SimpleWear.Droid.action.SEND_ACTION_UPDATE"
        const val ACTION_REQUESTBTDISCOVERABLE = "SimpleWear.Droid.action.REQUEST_BT_DISCOVERABLE"
        const val ACTION_SLEEPTIMERINSTALLEDSTATUS = "SimpleWear.Droid.action.SLEEP_TIMER_INSTALLED_STATUS"

        // Extras
        const val EXTRA_STATUS = "SimpleWear.Droid.extra.STATUS"
        const val EXTRA_ACTION_CHANGED = "SimpleWear.Droid.extra.ACTION_CHANGED"

        fun enqueueAction(context: Context, intentAction: String, inputDataMap: Map<String, Any>? = null) {
            val context = context.applicationContext
            when (intentAction) {
                ACTION_SENDSTATUSUPDATE, ACTION_REQUESTBTDISCOVERABLE, ACTION_SLEEPTIMERINSTALLEDSTATUS, SleepTimerHelper.ACTION_START_TIMER, SleepTimerHelper.ACTION_CANCEL_TIMER -> startWork(context, intentAction)
                SleepTimerHelper.ACTION_TIME_UPDATED -> startWork(context, if (inputDataMap == null) null else Data.Builder()
                        .putString(KEY_ACTION, intentAction)
                        .putAll(inputDataMap)
                        .build()
                )
            }
        }

        fun sendStatusUpdate(context: Context) {
            val context = context.applicationContext
            startWork(context, ACTION_SENDSTATUSUPDATE)
        }

        fun sendStatusUpdate(context: Context, @StatusAction statusAction: String, status: String) {
            val context = context.applicationContext
            startWork(context, Data.Builder()
                    .putString(KEY_ACTION, statusAction)
                    .putString(EXTRA_STATUS, status)
                    .build())
        }

        fun sendStatusUpdate(context: Context, @StatusAction statusAction: String, status: Int) {
            val context = context.applicationContext
            startWork(context, Data.Builder()
                    .putString(KEY_ACTION, statusAction)
                    .putInt(EXTRA_STATUS, status)
                    .build())
        }

        fun sendActionUpdate(context: Context, action: Actions) {
            val context = context.applicationContext
            startWork(context, Data.Builder()
                    .putString(KEY_ACTION, ACTION_SENDACTIONUPDATE)
                    .putInt(EXTRA_ACTION_CHANGED, action.value)
                    .build())
        }

        private fun startWork(context: Context, intentAction: String) {
            startWork(context, Data.Builder().putString(KEY_ACTION, intentAction).build())
        }

        private fun startWork(context: Context, inputData: Data?) {
            val context = context.applicationContext
            Logger.writeLine(Log.INFO, "%s: Requesting to start work", TAG)
            val updateRequest = OneTimeWorkRequest.Builder(WearableWorker::class.java)
            var intentAction: String? = null
            if (inputData != null) {
                intentAction = inputData.getString(KEY_ACTION)
                updateRequest.setInputData(inputData)
            }
            WorkManager.getInstance(context)
                    .enqueueUniqueWork(String.format(Locale.ROOT, "%s:%s_oneTime", TAG, intentAction),
                            ExistingWorkPolicy.REPLACE, updateRequest.build()
                    )
            Logger.writeLine(Log.INFO, "%s: One-time work enqueued", TAG)
        }
    }

    @StringDef(ACTION_SENDBATTERYUPDATE, ACTION_SENDWIFIUPDATE, ACTION_SENDBTUPDATE)
    @Retention(AnnotationRetention.SOURCE)
    annotation class StatusAction

    override suspend fun doWork(): Result {
        Logger.writeLine(Log.INFO, "%s: Work started", TAG)

        val intentAction = inputData.getString(KEY_ACTION)
        Logger.writeLine(Log.INFO, "%s: Action: %s", TAG, intentAction)

        val mWearMgr = WearableManager(applicationContext)

        if (mWearMgr.isWearNodesAvailable()) {
            if (ACTION_SENDSTATUSUPDATE == intentAction) {
                // Check if any devices are running and send an update
                mWearMgr.requestWearAppState()
            } else if (ACTION_SENDBATTERYUPDATE == intentAction) {
                val jsonData = inputData.getString(EXTRA_STATUS)
                mWearMgr.sendMessage(null, WearableHelper.BatteryPath, jsonData?.stringToBytes())
            } else if (ACTION_SENDWIFIUPDATE == intentAction) {
                mWearMgr.sendMessage(null, WearableHelper.WifiPath, byteArrayOf(inputData.getInt(EXTRA_STATUS, -1).toByte()))
            } else if (ACTION_SENDBTUPDATE == intentAction) {
                mWearMgr.sendMessage(null, WearableHelper.BluetoothPath, byteArrayOf(inputData.getInt(EXTRA_STATUS, -1).toByte()))
            } else if (ACTION_SENDACTIONUPDATE == intentAction) {
                val action = Actions.valueOf(inputData.getInt(EXTRA_ACTION_CHANGED, 0))
                mWearMgr.sendActionsUpdate(null, action)
            } else if (ACTION_REQUESTBTDISCOVERABLE == intentAction) {
                mWearMgr.sendMessage(null, WearableHelper.PingPath, null)
                mWearMgr.sendMessage(null, WearableHelper.BtDiscoverPath, null)
            } else if (SleepTimerHelper.ACTION_START_TIMER == intentAction) {
                mWearMgr.sendMessage(null, SleepTimerHelper.SleepTimerStartPath, null)
            } else if (SleepTimerHelper.ACTION_CANCEL_TIMER == intentAction) {
                mWearMgr.sendMessage(null, SleepTimerHelper.SleepTimerStopPath, null)
            } else if (SleepTimerHelper.ACTION_TIME_UPDATED == intentAction) {
                val timeStartMs = inputData.getLong(SleepTimerHelper.EXTRA_START_TIME_IN_MS, 0)
                val timeProgMs = inputData.getLong(SleepTimerHelper.EXTRA_TIME_IN_MS, 0)
                mWearMgr.sendSleepTimerUpdate(null, timeStartMs, timeProgMs)
            }
        }

        mWearMgr.unregister()

        return Result.success()
    }
}