package com.thewizrd.simplewear.wearable

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.ComponentActivity
import com.thewizrd.shared_resources.helpers.WearableHelper
import com.thewizrd.shared_resources.helpers.WearableHelper.toLaunchIntent
import com.thewizrd.shared_resources.utils.Logger

@SuppressLint("CustomSplashScreen")
class RemoteLaunchActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()

        intent?.data?.let { uri ->
            if (WearableHelper.isRemoteLaunchUri(uri)) {
                runCatching {
                    this.startActivity(uri.toLaunchIntent())
                }.onFailure { e ->
                    Logger.writeLine(
                        Log.ERROR,
                        e,
                        "%s: Unable to launch intent remotely - $uri",
                        this::class.java.simpleName
                    )
                }
            }
        }

        finishAffinity()
    }
}