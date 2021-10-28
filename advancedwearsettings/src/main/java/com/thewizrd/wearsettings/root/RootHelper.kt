package com.thewizrd.wearsettings.root

import com.topjohnwu.superuser.Shell

object RootHelper {
    fun isRootEnabled(): Boolean {
        return Shell.rootAccess()
    }
}