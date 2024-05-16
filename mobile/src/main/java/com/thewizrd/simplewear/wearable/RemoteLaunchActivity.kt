package com.thewizrd.simplewear.wearable

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
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
                    if (e !is ActivityNotFoundException) {
                        Logger.writeLine(
                            Log.ERROR,
                            e,
                            "RemoteLaunchActivity: Unable to launch intent remotely - $uri"
                        )
                    }
                }
            }
        }

        finishAffinity()
    }
}