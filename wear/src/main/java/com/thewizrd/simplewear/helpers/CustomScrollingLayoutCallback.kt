package com.thewizrd.simplewear.helpers

import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import kotlin.math.min

// Based on ScalingLazyColumn (wear-compose)
class CustomScrollingLayoutCallback : WearableLinearLayoutManager.LayoutCallback() {
    companion object {
        private const val edgeScale = 0.85f /* original: 0.5f */
        private const val edgeAlpha = 0.5f /* original: 0.5f */
        private const val minElementHeight = 0.5f /* original: 0.2f */
        private const val maxElementHeight = 0.8f /* original: 0.8f */
        private const val minTransitionArea = 0.35f /* original: 0.2f */
        private const val maxTransitionArea = 0.75f /* original: 0.6f */
    }

    private val scaleInterpolator = PathInterpolator(0.25f, 0f, 0.75f, 1f)

    override fun onLayoutFinished(child: View, parent: RecyclerView) {
        child.apply {
            val container = parent.parent as ViewGroup
            val viewPortStartPx = 0
            val viewPortEndPx = container.height
            val viewportPortHeight = (viewPortEndPx - viewPortStartPx).toFloat()
            val itemHeight = height.toFloat()
            val itemEdgeAsFractionOfViewport =
                min(bottom - viewPortStartPx, viewPortEndPx - top) / viewportPortHeight

            val heightAsFractionOfViewPort = itemHeight / viewportPortHeight
            if (itemEdgeAsFractionOfViewport > 0.0f && itemEdgeAsFractionOfViewport < 1.0f) {
                // Work out the scaling line based on size, this is a value between 0.0..1.0
                val sizeRatio: Float =
                    (
                            (heightAsFractionOfViewPort - minElementHeight) /
                                    (maxElementHeight - minElementHeight)
                            ).coerceIn(0f, 1f)

                val scalingLineAsFractionOfViewPort =
                    minTransitionArea +
                            (maxTransitionArea - minTransitionArea) *
                            sizeRatio

                if (itemEdgeAsFractionOfViewport < scalingLineAsFractionOfViewPort) {
                    // We are scaling
                    val fractionOfDiffToApplyRaw =
                        (scalingLineAsFractionOfViewPort - itemEdgeAsFractionOfViewport) /
                                scalingLineAsFractionOfViewPort
                    val fractionOfDiffToApplyInterpolated =
                        scaleInterpolator.getInterpolation(fractionOfDiffToApplyRaw)

                    val scaleToApply =
                        edgeScale +
                                (1.0f - edgeScale) *
                                (1.0f - fractionOfDiffToApplyInterpolated)
                    val alphaToApply =
                        edgeAlpha +
                                (1.0f - edgeAlpha) *
                                (1.0f - fractionOfDiffToApplyInterpolated)

                    scaleX = scaleToApply
                    scaleY = scaleToApply
                    alpha = alphaToApply
                } else {
                    scaleX = 1f
                    scaleY = 1f
                    alpha = 1f
                }
            }
        }
    }
}