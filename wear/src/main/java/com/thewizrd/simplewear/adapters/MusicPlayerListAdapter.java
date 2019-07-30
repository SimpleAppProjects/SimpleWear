package com.thewizrd.simplewear.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface;
import com.thewizrd.simplewear.controls.MusicPlayerItem;
import com.thewizrd.simplewear.controls.MusicPlayerViewModel;

import java.util.ArrayList;
import java.util.List;

public class MusicPlayerListAdapter extends RecyclerView.Adapter {
    private List<MusicPlayerViewModel> mDataset;
    private Activity mActivityContext;
    private RecyclerOnClickListenerInterface onClickListener;

    public void setOnClickListener(RecyclerOnClickListenerInterface onClickListener) {
        this.onClickListener = onClickListener;
    }

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    class ViewHolder extends RecyclerView.ViewHolder {
        MusicPlayerItem mItem;

        ViewHolder(MusicPlayerItem v) {
            super(v);
            mItem = v;

            mItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (onClickListener != null)
                        onClickListener.onClick(v, getAdapterPosition());
                }
            });
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public MusicPlayerListAdapter(Activity activity) {
        mDataset = new ArrayList<>();
        mActivityContext = activity;
    }

    public List<MusicPlayerViewModel> getDataset() {
        return mDataset;
    }

    @SuppressLint("NewApi")
    @NonNull
    @Override
    // Create new views (invoked by the layout manager)
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view
        MusicPlayerItem v = new MusicPlayerItem(parent.getContext());
        return new ViewHolder(v);
    }

    @Override
    // Replace the contents of a view (invoked by the layout manager)
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final ViewHolder vh = (ViewHolder) holder;
        final MusicPlayerViewModel viewModel = mDataset.get(position);

        vh.mItem.updateItem(viewModel);
    }

    @Override
    // Return the size of your dataset (invoked by the layout manager)
    public int getItemCount() {
        return mDataset.size();
    }

    public void updateItems(List<MusicPlayerViewModel> viewModels) {
        mDataset.clear();
        mDataset.addAll(viewModels);
        notifyDataSetChanged();
    }
}
