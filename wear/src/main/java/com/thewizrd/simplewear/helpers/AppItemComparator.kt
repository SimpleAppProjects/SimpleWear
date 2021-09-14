package com.thewizrd.simplewear.helpers

import com.thewizrd.simplewear.controls.AppItemViewModel

class AppItemComparator : Comparator<AppItemViewModel> {
    override fun compare(o1: AppItemViewModel?, o2: AppItemViewModel?): Int {
        if (o1?.appLabel == o2?.appLabel)
            return 0
        if (o1?.appLabel == null)
            return 1
        if (o2?.appLabel == null)
            return -1
        return o1.appLabel!!.compareTo(o2.appLabel!!, ignoreCase = true)
    }
}