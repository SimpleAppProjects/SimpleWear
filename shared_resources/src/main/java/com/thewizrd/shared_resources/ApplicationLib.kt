package com.thewizrd.shared_resources

import android.content.Context
import android.content.SharedPreferences
import com.thewizrd.shared_resources.helpers.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope

public lateinit var appLib: ApplicationLib

abstract class ApplicationLib {
    abstract val context: Context
    abstract val preferences: SharedPreferences
    abstract val appState: AppState
    abstract val isPhone: Boolean
    open val appScope: CoroutineScope = MainScope()
}