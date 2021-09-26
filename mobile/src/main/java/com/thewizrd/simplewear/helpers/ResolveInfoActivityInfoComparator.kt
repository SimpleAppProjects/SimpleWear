package com.thewizrd.simplewear.helpers

import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import java.text.Collator

/**
 * Compares ResolveInfo by activity info label
 */
class ResolveInfoActivityInfoComparator(private val pm: PackageManager) : Comparator<ResolveInfo> {
    private val collator = Collator.getInstance()

    override fun compare(p0: ResolveInfo?, p1: ResolveInfo?): Int {
        val sa = p0?.let {
            it.activityInfo.loadLabel(pm) ?: pm.getApplicationLabel(it.activityInfo.applicationInfo)
            ?: it.activityInfo.packageName
        } ?: ""
        val sb = p1?.let {
            it.activityInfo.loadLabel(pm) ?: pm.getApplicationLabel(it.activityInfo.applicationInfo)
            ?: it.activityInfo.packageName
        } ?: ""

        return collator.compare(sa.toString(), sb.toString())
    }
}