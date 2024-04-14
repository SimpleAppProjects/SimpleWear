package com.thewizrd.simplewear.ui.navigation

import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.controls.AppItemViewModel

sealed class Screen(
    val route: String
) {
    data object Dashboard : Screen("dashboard")
    data object AppLauncher : Screen("appLauncher")
    data object CallManager : Screen("callManager")
    data object ValueAction : Screen("valueAction") {
        fun getRoute(actionType: Actions, streamType: AudioStreamType? = null): String {
            return if (streamType != null) {
                "${route}/${actionType.value}?streamType=${streamType}"
            } else {
                "${route}/${actionType.value}"
            }

        }
    }

    data object MediaPlayerList : Screen("mediaPlayerList")
    data object MediaPlayer : Screen("mediaPlayer") {
        fun autoLaunch(): String {
            return "$route?autoLaunch=true"
        }

        fun getRoute(app: String): String {
            return "$route?app=$app"
        }

        fun getRoute(model: AppItemViewModel): String {
            val modelLite = AppItemViewModel().apply {
                packageName = model.packageName
                activityName = model.activityName
                this.appLabel = model.appLabel
            }

            return "$route?app=${JSONParser.serializer(modelLite, AppItemViewModel::class.java)}"
        }
    }

    data object DashboardConfig : Screen("dashboardConfig")
    data object DashboardTileConfig : Screen("dashboardTileConfig")
}