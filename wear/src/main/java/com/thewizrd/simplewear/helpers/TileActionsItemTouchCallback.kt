package com.thewizrd.simplewear.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.simplewear.adapters.AddButtonAdapter
import com.thewizrd.simplewear.adapters.TileActionAdapter
import java.util.*

class TileActionsItemTouchCallback(private val adapter: TileActionAdapter) :
    ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END,
        0
    ) {
    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return if (viewHolder is AddButtonAdapter.ViewHolder || target is AddButtonAdapter.ViewHolder) {
            false
        } else {
            val from = viewHolder.bindingAdapterPosition
            val to = target.bindingAdapterPosition

            val items = adapter.currentList.toMutableList()
            Collections.swap(items, from, to)
            adapter.submitList(items)

            true
        }
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun isItemViewSwipeEnabled(): Boolean {
        return false
    }

    override fun isLongPressDragEnabled(): Boolean {
        return false
    }
}