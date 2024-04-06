package com.thewizrd.simplewear.ui.simplewear

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.AudioStreamType
import com.thewizrd.shared_resources.utils.JSONParser
import com.thewizrd.simplewear.controls.AppItemViewModel
import com.thewizrd.simplewear.ui.navigation.Screen
import com.thewizrd.simplewear.ui.theme.WearAppTheme
import com.thewizrd.simplewear.ui.theme.findActivity

@Composable
fun MediaPlayer(
    startDestination: String = Screen.MediaPlayerList.route
) {
    WearAppTheme {
        val context = LocalContext.current
        val activity = context.findActivity()

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
            composable(route = Screen.MediaPlayerList.route) {
                MediaPlayerListUi(
                    navController = navController,
                    swipeToDismissBoxState = swipeToDismissBoxState
                )
            }

            composable(
                route = Screen.MediaPlayer.route,
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
                    swipeToDismissBoxState = swipeToDismissBoxState,
                    autoLaunch = autoLaunch ?: (app == null),
                    app = app
                )
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
            }
        }
    }
}