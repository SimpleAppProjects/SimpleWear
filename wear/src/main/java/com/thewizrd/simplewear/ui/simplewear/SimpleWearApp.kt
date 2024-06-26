package com.thewizrd.simplewear.ui.simplewear

import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.activity
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.simplewear.media.MediaPlayerActivity
import com.thewizrd.simplewear.preferences.DashboardConfigActivity
import com.thewizrd.simplewear.preferences.DashboardTileConfigActivity
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.WearAppTheme

@Composable
fun SimpleWearApp(
    startDestination: String = Screen.Dashboard.route
) {
    WearAppTheme {
        val context = LocalContext.current

        val navController = rememberSwipeDismissableNavController()
        val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
        val swipeDismissNavState = rememberSwipeDismissableNavHostState(
            swipeToDismissBoxState = swipeToDismissBoxState
        )

        SwipeDismissableNavHost(
            navController = navController,
            startDestination = startDestination,
            state = swipeDismissNavState
        ) {
            composable(Screen.Dashboard.route) {
                Dashboard(navController = navController)

                LaunchedEffect(navController) {
                    AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                        putString("screen", Screen.Dashboard.route)
                    })
                }
            }

            composable(
                route = Screen.ValueAction.route + "/{actionId}?streamType={streamType}",
                arguments = listOf(
                    navArgument("actionId") {
                        type = NavType.IntType
                    },
                    navArgument("streamType") {
                        type = NavType.EnumType(AudioStreamType::class.java)
                        defaultValue = AudioStreamType.MUSIC
                    }
                )
            ) { backstackEntry ->
                val actionType = backstackEntry.arguments?.getInt("actionId")?.let {
                    Actions.valueOf(it)
                }
                val streamType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    backstackEntry.arguments?.getSerializable(
                        "streamType",
                        AudioStreamType::class.java
                    )
                } else {
                    backstackEntry.arguments?.getSerializable("streamType") as AudioStreamType
                }

                ValueActionScreen(
                    actionType = actionType ?: Actions.VOLUME,
                    audioStreamType = streamType
                )

                LaunchedEffect(navController, actionType) {
                    AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                        putString("screen", Screen.ValueAction.route)
                        actionType?.let {
                            putString("actionType", it.name)
                        }
                    })
                }
            }

            activity(route = Screen.MediaPlayerList.route) {
                targetPackage = context.packageName
                activityClass = MediaPlayerActivity::class
            }

            composable(Screen.AppLauncher.route) {
                AppLauncherScreen(
                    navController = navController,
                    swipeToDismissBoxState = swipeToDismissBoxState
                )

                LaunchedEffect(navController) {
                    AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                        putString("screen", Screen.AppLauncher.route)
                    })
                }
            }

            composable(Screen.CallManager.route) {
                CallManagerUi(navController = navController)

                LaunchedEffect(navController) {
                    AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                        putString("screen", Screen.CallManager.route)
                    })
                }
            }

            activity(route = Screen.DashboardConfig.route) {
                targetPackage = context.packageName
                activityClass = DashboardConfigActivity::class
            }

            activity(route = Screen.DashboardTileConfig.route) {
                targetPackage = context.packageName
                activityClass = DashboardTileConfigActivity::class
            }
        }
    }
}