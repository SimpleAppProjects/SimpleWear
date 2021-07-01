package com.thewizrd.simplewear.adapters

import android.app.Activity
import android.os.Build
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableRecyclerView
import com.thewizrd.shared_resources.actions.*
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.controls.ActionButton
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import java.util.*

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

        for (action in Actions.values()) {
            when (action) {
                Actions.WIFI,
                Actions.BLUETOOTH,
                Actions.MOBILEDATA,
                Actions.TORCH ->
                    mDataset.add(ActionButtonViewModel(ToggleAction(action, true)))
                Actions.LOCATION ->
                    mDataset.add(ActionButtonViewModel(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                                MultiChoiceAction(action)
                            else
                                ToggleAction(action, true)
                    ))
                Actions.LOCKSCREEN,
                Actions.MUSICPLAYBACK,
                Actions.SLEEPTIMER,
                Actions.APPS ->
                    mDataset.add(ActionButtonViewModel(NormalAction(action)))
                Actions.VOLUME ->
                    mDataset.add(ActionButtonViewModel(ValueAction(action, ValueDirection.UP)))
                Actions.DONOTDISTURB ->
                    mDataset.add(ActionButtonViewModel(
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
                                MultiChoiceAction(action)
                            else
                                ToggleAction(action, true)
                    ))
                Actions.RINGER ->
                    mDataset.add(ActionButtonViewModel(MultiChoiceAction(action)))
            }
        }
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    inner class ViewHolder(var mButton: ActionButton) : RecyclerView.ViewHolder(mButton)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // create a new view
        val v = ActionButton(parent.context)
        val recyclerView: RecyclerView = parent as WearableRecyclerView
        val viewLayoutMgr = recyclerView.layoutManager
        val vParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        val innerPadding =
            parent.getContext().resources.getDimensionPixelSize(R.dimen.inner_layout_padding)

        if (viewLayoutMgr is GridLayoutManager) {
            v.isExpanded = false
            var horizPadding = 0
            try {
                val spanCount = viewLayoutMgr.spanCount
                val viewWidth = parent.getMeasuredWidth() - innerPadding * 2
                val colWidth = viewWidth / spanCount
                horizPadding = colWidth - v.fabCustomSize
            } catch (ignored: Exception) {
            }

            val vertPadding = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                6f,
                parent.getContext().resources.displayMetrics
            ).toInt()
            v.setPaddingRelative(0, 0, 0, 0)
            recyclerView.setPaddingRelative(innerPadding, 0, innerPadding, 0)
            vParams.setMargins(horizPadding / 2, vertPadding, horizPadding / 2, vertPadding)
        } else if (viewLayoutMgr is LinearLayoutManager) {
            v.isExpanded = true
            v.setPaddingRelative(innerPadding, 0, innerPadding, 0)
            recyclerView.setPaddingRelative(0, 0, 0, 0)
        }

        v.layoutParams = vParams

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
            Actions.WIFI, Actions.BLUETOOTH, Actions.MOBILEDATA, Actions.TORCH -> {
                ActionItemType.TOGGLE_ACTION
            }
            Actions.LOCATION -> ActionItemType.TOGGLE_ACTION
            Actions.LOCKSCREEN, Actions.MUSICPLAYBACK, Actions.SLEEPTIMER, Actions.APPS -> {
                ActionItemType.ACTION
            }
            Actions.VOLUME -> ActionItemType.VALUE_ACTION
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
        val idx = action.actionType.value
        mDataset[idx] = action
        notifyItemChanged(idx)
    }
}