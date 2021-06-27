package com.thewizrd.simplewear.wearable

import android.content.Context
import android.util.Log
import androidx.annotation.StringDef
import androidx.work.*
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.shared_resources.utils.stringToBytes

class WearableWorker(context: Context, workerParams: WorkerParameters) : CoroutineWorker(context, workerParams) {
    companion object {
        private const val TAG = "WearableWorker"

        // Actions
        private const val KEY_ACTION = "action"
        const val ACTION_SENDSTATUSUPDATE = "SimpleWear.Droid.action.SEND_STATUS_UPDATE"
        const val ACTION_SENDBATTERYUPDATE = "SimpleWear.Droid.action.SEND_BATTERY_UPDATE"
        const val ACTION_SENDWIFIUPDATE = "SimpleWear.Droid.action.SEND_WIFI_UPDATE"
        const val ACTION_SENDBTUPDATE = "SimpleWear.Droid.action.SEND_BT_UPDATE"
        const val ACTION_SENDACTIONUPDATE = "SimpleWear.Droid.action.SEND_ACTION_UPDATE"
        const val ACTION_REQUESTBTDISCOVERABLE = "SimpleWear.Droid.action.REQUEST_BT_DISCOVERABLE"

        // Extras
        const val EXTRA_STATUS = "SimpleWear.Droid.extra.STATUS"
        const val EXTRA_ACTION_CHANGED = "SimpleWear.Droid.extra.ACTION_CHANGED"

        fun enqueueAction(context: Context, intentAction: String) {
            when (intentAction) {
                ACTION_SENDSTATUSUPDATE,
                ACTION_REQUESTBTDISCOVERABLE -> {
                    startWork(context.applicationContext, intentAction)
                }
            }
        }

        fun sendStatusUpdate(context: Context) {
            startWork(context.applicationContext, ACTION_SENDSTATUSUPDATE)
        }

        fun sendStatusUpdate(context: Context, @StatusAction statusAction: String, status: String) {
            startWork(
                context.applicationContext, Data.Builder()
                    .putString(KEY_ACTION, statusAction)
                    .putString(EXTRA_STATUS, status)
                    .build()
            )
        }

        fun sendStatusUpdate(context: Context, @StatusAction statusAction: String, status: Int) {
            startWork(
                context.applicationContext, Data.Builder()
                    .putString(KEY_ACTION, statusAction)
                    .putInt(EXTRA_STATUS, status)
                    .build()
            )
        }

        fun sendActionUpdate(context: Context, action: Actions) {
            startWork(
                context.applicationContext, Data.Builder()
                    .putString(KEY_ACTION, ACTION_SENDACTIONUPDATE)
                    .putInt(EXTRA_ACTION_CHANGED, action.value)
                    .build()
            )
        }

        private fun startWork(context: Context, intentAction: String) {
            startWork(context, Data.Builder().putString(KEY_ACTION, intentAction).build())
        }

        private fun startWork(context: Context, inputData: Data?) {
            Logger.writeLine(Log.INFO, "%s: Requesting to start work", TAG)
            val updateRequest = OneTimeWorkRequest.Builder(WearableWorker::class.java)
            if (inputData != null) {
                updateRequest.setInputData(inputData)
            }
            WorkManager.getInstance(context.applicationContext).enqueue(updateRequest.build())
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
            when (intentAction) {
                ACTION_SENDSTATUSUPDATE -> {
                    // Check if any devices are running and send an update
                    mWearMgr.requestWearAppState()
                }
                ACTION_SENDBATTERYUPDATE -> {
                    val jsonData = inputData.getString(EXTRA_STATUS)
                    mWearMgr.sendMessage(
                        null,
                        WearableHelper.BatteryPath,
                        jsonData?.stringToBytes()
                    )
                }
                ACTION_SENDWIFIUPDATE -> {
                    mWearMgr.sendMessage(
                        null,
                        WearableHelper.WifiPath,
                        byteArrayOf(inputData.getInt(EXTRA_STATUS, -1).toByte())
                    )
                }
                ACTION_SENDBTUPDATE -> {
                    mWearMgr.sendMessage(
                        null,
                        WearableHelper.BluetoothPath,
                        byteArrayOf(inputData.getInt(EXTRA_STATUS, -1).toByte())
                    )
                }
                ACTION_SENDACTIONUPDATE -> {
                    val action = Actions.valueOf(inputData.getInt(EXTRA_ACTION_CHANGED, 0))
                    mWearMgr.sendActionsUpdate(null, action)
                }
                ACTION_REQUESTBTDISCOVERABLE -> {
                    mWearMgr.sendMessage(null, WearableHelper.PingPath, null)
                    mWearMgr.sendMessage(null, WearableHelper.BtDiscoverPath, null)
                }
            }
        }

        mWearMgr.unregister()

        return Result.success()
    }
}