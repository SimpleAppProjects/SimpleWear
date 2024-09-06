package com.thewizrd.simplewear.controls

import androidx.navigation.NavController
import com.thewizrd.shared_resources.actions.Action
import com.thewizrd.shared_resources.actions.ActionStatus
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.actions.MultiChoiceAction
import com.thewizrd.shared_resources.actions.NormalAction
import com.thewizrd.shared_resources.actions.ToggleAction
import com.thewizrd.shared_resources.actions.ValueAction
import com.thewizrd.shared_resources.controls.ActionButtonViewModel
import com.thewizrd.shared_resources.sleeptimer.SleepTimerHelper
import com.thewizrd.simplewear.ui.navigation.Screen

fun ActionButtonViewModel.onClick(
    navController: NavController,
    onActionChanged: (Action) -> Unit,
    onActionStatus: (Action) -> Unit
) {
    action.isActionSuccessful = true

    if (action is ValueAction) {
        navController.navigate("${Screen.ValueAction.route}/${actionType.value}")
    } else if (action is NormalAction && action.actionType == Actions.MUSICPLAYBACK) {
        navController.navigate(Screen.MediaPlayerList.route)
    } else if (action is NormalAction && action.actionType == Actions.SLEEPTIMER) {
        if (SleepTimerHelper.isSleepTimerInstalled()) {
            SleepTimerHelper.launchSleepTimer()
        } else {
            action.setActionSuccessful(ActionStatus.PERMISSION_DENIED)

            onActionStatus.invoke(action)
        }
    } else if (action is NormalAction && action.actionType == Actions.APPS) {
        navController.navigate(Screen.AppLauncher.route)
    } else if (action is NormalAction && action.actionType == Actions.PHONE) {
        navController.navigate(Screen.CallManager.route)
    } else if (action is NormalAction && action.actionType == Actions.GESTURES) {
        navController.navigate(Screen.GesturesAction.route)
    } else if (action is NormalAction && action.actionType == Actions.TIMEDACTION) {
        navController.navigate(Screen.TimedActions.route)
    } else {
        if (action is ToggleAction) {
            val tA = action as ToggleAction
            tA.isEnabled = !tA.isEnabled
            buttonState = null
        } else if (action is MultiChoiceAction) {
            val mA = action as MultiChoiceAction
            val currentChoice = mA.choice
            val newChoice = currentChoice + 1
            mA.choice = newChoice
            updateIconAndLabel()
        }

        onActionChanged.invoke(action)
    }
}