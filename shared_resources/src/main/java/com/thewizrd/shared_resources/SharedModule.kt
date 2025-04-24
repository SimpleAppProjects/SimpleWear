package com.thewizrd.shared_resources

import android.content.Context
import com.thewizrd.shared_resources.utils.Logger

private lateinit var _sharedDeps: SharedModule

var sharedDeps: SharedModule
    get() = _sharedDeps
    set(value) {
        _sharedDeps = value
        value.init()
    }

abstract class SharedModule {
    abstract val context: Context

    internal fun init() {
        // Initialize logger
        Logger.init(context)
    }
}