package com.thewizrd.simplewear.adapters

import androidx.core.util.ObjectsCompat
import androidx.recyclerview.widget.DiffUtil
import com.thewizrd.simplewear.controls.AppItemViewModel

class AppItemDiffer : DiffUtil.ItemCallback<AppItemViewModel>() {
    override fun areItemsTheSame(oldItem: AppItemViewModel, newItem: AppItemViewModel): Boolean {
        return ObjectsCompat.equals(oldItem.packageName, newItem.packageName) &&
                ObjectsCompat.equals(oldItem.activityName, newItem.activityName)
    }

    override fun areContentsTheSame(oldItem: AppItemViewModel, newItem: AppItemViewModel): Boolean {
        return ObjectsCompat.equals(oldItem, newItem)
    }
}