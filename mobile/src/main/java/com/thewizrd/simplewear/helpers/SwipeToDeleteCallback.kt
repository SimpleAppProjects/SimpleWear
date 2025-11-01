package com.thewizrd.simplewear.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.utils.ContextUtils.getAttrColor
import com.thewizrd.simplewear.R

abstract class SwipeToDeleteCallback(context: Context) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
    private val deleteIcon = DrawableCompat.wrap(
        ContextCompat.getDrawable(context, R.drawable.ic_delete_outline)!!.mutate()
    )
    private val deleteBackground = context.getAttrColor(R.attr.colorError).toDrawable()
    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    init {
        DrawableCompat.setTint(deleteIcon, context.getAttrColor(R.attr.colorOnError))
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return false
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val itemView = viewHolder.itemView
        val itemHeight = itemView.bottom - itemView.top
        val isCanceled = dX == 0f && !isCurrentlyActive

        if (isCanceled) {
            clearCanvas(
                c,
                itemView.right + dX,
                itemView.top.toFloat(),
                itemView.right.toFloat(),
                itemView.bottom.toFloat()
            )
        } else {
            val iconMargin = (itemHeight - deleteIcon.intrinsicHeight) / 2
            val iconLeft = itemView.right - iconMargin - deleteIcon.intrinsicHeight
            val iconRight = itemView.right - iconMargin
            val iconTop =
                itemView.top + (itemView.bottom - itemView.top - deleteIcon.intrinsicHeight) / 2
            val iconBottom = iconTop + deleteIcon.intrinsicHeight

            deleteBackground.setBounds(
                itemView.right - itemView.width,
                itemView.top,
                itemView.right,
                itemView.bottom
            )
            deleteBackground.draw(c)

            deleteIcon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
            deleteIcon.draw(c)
        }

        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    private fun clearCanvas(c: Canvas?, left: Float, top: Float, right: Float, bottom: Float) {
        c?.drawRect(left, top, right, bottom, clearPaint)
    }
}