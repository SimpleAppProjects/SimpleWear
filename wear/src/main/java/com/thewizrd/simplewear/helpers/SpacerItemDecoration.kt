package com.thewizrd.simplewear.helpers

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacerItemDecoration : RecyclerView.ItemDecoration {
    private val horizontalSpace: Int?
    private val verticalSpace: Int?

    constructor(space: Int) : super() {
        horizontalSpace = space
        verticalSpace = space
    }

    constructor(horizontalSpace: Int? = null, verticalSpace: Int? = null) : super() {
        this.horizontalSpace = horizontalSpace
        this.verticalSpace = verticalSpace
    }

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        super.getItemOffsets(outRect, view, parent, state)

        verticalSpace?.let {
            outRect.top = it / 2
            outRect.bottom = it / 2
        }
        horizontalSpace?.let {
            outRect.left = it / 2
            outRect.right = it / 2
        }
    }
}