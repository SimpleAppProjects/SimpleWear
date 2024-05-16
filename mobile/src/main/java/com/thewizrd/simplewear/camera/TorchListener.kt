package com.thewizrd.simplewear.camera

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.Logger
import com.thewizrd.simplewear.App
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.wearable.WearableWorker
import java.util.concurrent.Executors

object TorchListener {
    var isTorchEnabled: Boolean = false
        private set

    var isRegistered = false
        private set

    private val torchCallback = lazy {
        object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == primaryCameraId.value) {
                    val context = App.instance.appContext

                    isTorchEnabled = enabled

                    if (!enabled && PhoneStatusHelper.isTorchEnabled(context)) {
                        PhoneStatusHelper.setTorchEnabled(context, false)
                    }

                    WearableWorker.sendActionUpdate(context, Actions.TORCH)
                }
            }
        }
    }

    private val primaryCameraId = lazy {
        val cameraMgr =
            App.instance.appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cameraMgr.cameraIdList[0]
    }

    fun registerListener(context: Context): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            val cameraMgr = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                cameraMgr.registerTorchCallback(
                    Executors.newSingleThreadExecutor(),
                    torchCallback.value
                )
            } else {
                cameraMgr.registerTorchCallback(
                    torchCallback.value,
                    Handler(Looper.getMainLooper())
                )
            }

            true
        }.getOrElse {
            Logger.writeLine(Log.ERROR, it)
            false
        }.apply {
            isRegistered = this
        }
    }

    fun unregisterListener(context: Context) {
        runCatching {
            if (torchCallback.isInitialized()) {
                val appContext = context.applicationContext
                val cameraMgr = appContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager

                cameraMgr.unregisterTorchCallback(torchCallback.value)
            }
        }

        isRegistered = false
    }
}