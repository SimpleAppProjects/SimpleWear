package com.thewizrd.simplewear.ui.simplewear

import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.utils.AnalyticsLogger
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.shared_resources.utils.getSerializableCompat
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.WearAppTheme

@Composable
fun MediaPlayer(
    startDestination: String = Screen.MediaPlayer.autoLaunch()
) {
    WearAppTheme {
        val navController = rememberSwipeDismissableNavController()

        AppScaffold(
            timeText = {
                TimeText(backgroundColor = TimeTextDefaults.backgroundColor().copy(alpha = 0.5f))
            }
        ) {
            SwipeDismissableNavHost(
                navController = navController,
                startDestination = startDestination
            ) {
                composable(route = Screen.MediaPlayerList.route) {
                    MediaPlayerListUi(navController = navController)

                    LaunchedEffect(navController) {
                        AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                            putString("screen", Screen.MediaPlayerList.route)
                        })
                    }
                }

                composable(
                    route = Screen.MediaPlayer.route + "?autoLaunch={autoLaunch}&app={app}",
                    arguments = listOf(
                        navArgument("autoLaunch") {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                        navArgument("app") {
                            type = NavType.StringType
                            nullable = true
                        }
                    )
                ) { backstackEntry ->
                    val autoLaunch = backstackEntry.arguments?.getBoolean("autoLaunch")
                    val app = remember(backstackEntry) {
                        JSONParser.deserializer(
                            backstackEntry.arguments?.getString("app"),
                            AppItemViewModel::class.java
                        )
                    }

                    MediaPlayerUi(
                        navController = navController,
                        autoLaunch = autoLaunch ?: (app == null),
                        app = app
                    )

                    LaunchedEffect(navController) {
                        AnalyticsLogger.logEvent("nav_route", Bundle().apply {
                            putString("screen", Screen.MediaPlayer.route)
                            app?.let {
                                putString("app", it.packageName)
                            }
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
                    val streamType = backstackEntry.arguments?.getSerializableCompat(
                        "streamType",
                        AudioStreamType::class.java
                    )

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
            }
        }
    }
}