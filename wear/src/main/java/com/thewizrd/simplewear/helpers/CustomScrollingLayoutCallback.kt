package com.thewizrd.simplewear.helpers

import android.util.Log
import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import kotlin.math.abs
import kotlin.math.min

class CustomScrollingLayoutCallback : WearableLinearLayoutManager.LayoutCallback() {
    companion object {
        /** How much should we scale the icon at most.  */
        private const val MAX_ICON_PROGRESS = 0.65f
    }

    private var progressToCenter: Float = 0f

    override fun onLayoutFinished(child: View, parent: RecyclerView) {
        val idx = parent.indexOfChild(child)
        child.apply {
            // Figure out % progress from top to bottom
            val centerOffset = height.toFloat() / 2.0f / parent.height.toFloat()
            val yRelativeToCenterOffset = y / parent.height + centerOffset

            // Normalize for center
            progressToCenter = abs(0.5f - yRelativeToCenterOffset)
            // Adjust to the maximum scale
            progressToCenter = min(progressToCenter, MAX_ICON_PROGRESS)

            scaleX = 1 + min(0f, progressToCenter - 0.2f)
            scaleY = 1 + min(0f, progressToCenter - 0.2f)

            if (idx == 0) {
                Log.d("CustomScroller", "centerOffset = $centerOffset")
                Log.d("CustomScroller", "yRelativeToCenterOffset = $yRelativeToCenterOffset")
                Log.d("CustomScroller", "progressToCenter = $progressToCenter")
                Log.d("CustomScroller", "scaleX = $scaleX; scaleY = $scaleY")
            }
        }
    }
}