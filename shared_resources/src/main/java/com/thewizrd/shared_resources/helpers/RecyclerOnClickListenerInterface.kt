package com.thewizrd.shared_resources.helpers

import android.view.View

interface RecyclerOnClickListenerInterface {
    fun onClick(view: View, position: Int)
}

interface ListAdapterOnClickInterface<T> {
    fun onClick(view: View, item: T)
}