package com.thewizrd.simplewear.helpers;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

public class SimpleRecyclerViewAdapterObserver extends RecyclerView.AdapterDataObserver {
    @Override
    public void onChanged() {
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
        onChanged();
    }

    @Override
    public void onItemRangeInserted(int positionStart, int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeRemoved(int positionStart, int itemCount) {
        onChanged();
    }

    @Override
    public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
        onChanged();
    }
}