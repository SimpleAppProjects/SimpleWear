package com.thewizrd.shared_resources

import android.content.Context
import com.thewizrd.shared_resources.helpers.AppState

interface ApplicationLib {
    val appContext: Context
    val applicationState: AppState
    val isPhone: Boolean
}