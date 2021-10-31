package com.thewizrd.wearsettings.root

import androidx.annotation.WorkerThread
import com.topjohnwu.superuser.Shell

object RootHelper {
    @WorkerThread
    fun isRootEnabled(): Boolean {
        return Shell.rootAccess()
    }
}