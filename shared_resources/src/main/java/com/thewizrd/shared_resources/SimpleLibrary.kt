package com.thewizrd.shared_resources

import android.annotation.SuppressLint
import android.content.Context

class SimpleLibrary private constructor() {
    private var mApp: ApplicationLib? = null
    private var mAppContext: Context? = null

    private constructor(app: ApplicationLib) : this() {
        this.mApp = app
        this.mAppContext = app.appContext
    }

    companion object {
        @SuppressLint("StaticFieldLeak")
        @JvmStatic
        private var sSimpleLib: SimpleLibrary? = null

        @JvmStatic
        val instance: SimpleLibrary
            get() {
                if (sSimpleLib == null) {
                    sSimpleLib = SimpleLibrary()
                }

                return sSimpleLib!!
            }

        @JvmStatic
        fun initialize(app: ApplicationLib) {
            if (sSimpleLib == null) {
                sSimpleLib = SimpleLibrary(app)
            } else {
                sSimpleLib!!.mApp = app
                sSimpleLib!!.mAppContext = app.appContext
            }
        }

        fun unregister() {
            sSimpleLib = null
        }
    }

    val app: ApplicationLib
        get() {
            return mApp!!
        }

    val appContext: Context
        get() {
            return mAppContext!!
        }
}