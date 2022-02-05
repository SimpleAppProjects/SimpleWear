package com.thewizrd.simplewear.adapters

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButton
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.preferences.Settings

class ActionItemAdapter(activity: Activity) : RecyclerView.Adapter<ActionItemAdapter.ViewHolder>() {
    private object ActionItemType {
        const val ACTION = 0
        const val TOGGLE_ACTION = 1
        const val VALUE_ACTION = 2
        const val READONLY_ACTION = 3
        const val MULTICHOICE_ACTION = 4
    }

    private val mDataset: MutableList<ActionButtonViewModel>
    private val mActivityContext: Activity
    var isItemsClickable = true

    // Provide a suitable constructor (depends on the kind of dataset)
    init {
        mDataset = ArrayList()
        mActivityContext = activity

        val actions = Settings.getDashboardConfig() ?: Actions.values().toMutableList()
        resetActions(actions)
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    inner class ViewHolder(var mButton: ActionButton) : RecyclerView.ViewHolder(mButton)

    private fun resetActions(actions: List<Actions>) {
        mDataset.clear()

        actions.forEach {
            mDataset.add(ActionButtonViewModel.getViewModelFromAction(it))
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // create a new view
        val v = ActionButton(parent.context)
        val recyclerView = parent as RecyclerView
        val viewLayoutMgr = recyclerView.layoutManager
        val viewParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (viewLayoutMgr is GridLayoutManager) {
            val collapsedSize =
                parent.context.resources.getDimensionPixelSize(R.dimen.action_button_collapsed_size)
            val horizPadding = runCatching {
                val spanCount = viewLayoutMgr.spanCount
                val viewWidth = parent.getMeasuredWidth() - parent.paddingStart - parent.paddingEnd
                val colWidth = viewWidth / spanCount
                colWidth - collapsedSize
            }.getOrNull() ?: 0
            val vertPadding = parent.getContext().dpToPx(6f).toInt()

            v.isExpanded = false
            val icoSize = collapsedSize / 2
            v.setIconSize(icoSize, 0)

            viewParams.width = collapsedSize
            viewParams.height = collapsedSize
            viewParams.setMargins(horizPadding / 2, vertPadding, horizPadding / 2, vertPadding)
        } else if (viewLayoutMgr is LinearLayoutManager) {
            val vertPadding = parent.getContext().dpToPx(2f).toInt()

            v.isExpanded = true
            viewParams.setMargins(0, vertPadding, 0, vertPadding)
        }

        v.layoutParams = viewParams

        return ViewHolder(v)
    }

    // Replace the contents of a view (invoked by the layout manager)
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        val actionVM = mDataset[position]

        holder.mButton.updateButton(actionVM)

        if (holder.itemViewType != ActionItemType.READONLY_ACTION) {
            holder.mButton.setOnClickListener(View.OnClickListener {
                if (!isItemsClickable) return@OnClickListener
                actionVM.onClick(mActivityContext)
                notifyItemChanged(position)
            })
        } else {
            holder.mButton.setOnClickListener(null)
        }

        if (holder.mButton.isExpanded) {
            holder.mButton.setOnLongClickListener(null)
        } else {
            holder.mButton.setOnLongClickListener { v ->
                var text = actionVM.actionLabel
                if (!actionVM.stateLabel.isNullOrBlank()) {
                    text = String.format("%s: %s", text, actionVM.stateLabel)
                }
                Toast.makeText(v.context, text, Toast.LENGTH_SHORT).show()
                true
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (mDataset[position].actionType) {
            Actions.WIFI, Actions.BLUETOOTH, Actions.MOBILEDATA, Actions.TORCH, Actions.HOTSPOT -> {
                ActionItemType.TOGGLE_ACTION
            }
            Actions.LOCATION -> ActionItemType.TOGGLE_ACTION
            Actions.LOCKSCREEN, Actions.MUSICPLAYBACK, Actions.SLEEPTIMER, Actions.APPS, Actions.PHONE -> {
                ActionItemType.ACTION
            }
            Actions.VOLUME, Actions.BRIGHTNESS -> ActionItemType.VALUE_ACTION
            Actions.DONOTDISTURB -> {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    ActionItemType.MULTICHOICE_ACTION
                } else {
                    ActionItemType.TOGGLE_ACTION
                }
            }
            Actions.RINGER -> ActionItemType.MULTICHOICE_ACTION
            else -> ActionItemType.TOGGLE_ACTION
        }
    }

    // Return the size of your dataset (invoked by the layout manager)
    override fun getItemCount(): Int {
        return mDataset.size
    }

    fun updateButton(action: ActionButtonViewModel) {
        for (idx in 0 until mDataset.size) {
            val item = mDataset[idx]

            if (item.actionType == action.actionType) {
                mDataset[idx] = action
                notifyItemChanged(idx)
                break
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateActions(actions: List<Actions>?) {
        resetActions(actions ?: Actions.values().toList())
        notifyDataSetChanged()
    }
}