package com.thewizrd.simplewear.adapters;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thewizrd.shared_resources.helpers.Action;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.utils.JSONParser;
import com.thewizrd.simplewear.WearableListenerActivity;
import com.thewizrd.simplewear.controls.ToggleActionButton;

import java.util.ArrayList;
import java.util.List;

public class ToggleActionAdapter extends RecyclerView.Adapter {
    private static class ActionItemType {
        public final static int TOGGLE_ACTION = 1;
        public final static int VALUE_ACTION = 2;
        public final static int READONLY_ACTION = 3;
    }

    private List<Action> mDataset;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    class ViewHolder extends RecyclerView.ViewHolder {
        public ToggleActionButton mToggleButton;

        public ViewHolder(ToggleActionButton v) {
            super(v);
            mToggleButton = v;
        }
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public ToggleActionAdapter() {
        mDataset = new ArrayList<>();

        for (Actions action : Actions.values()) {
            mDataset.add(new ToggleAction(action, false));
        }
    }

    @SuppressLint("NewApi")
    @NonNull
    @Override
    // Create new views (invoked by the layout manager)
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view
        ToggleActionButton v = new ToggleActionButton(parent.getContext());

        RecyclerView.LayoutParams vParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, parent.getContext().getResources().getDisplayMetrics());
        vParams.setMargins(0, padding, 0, padding);
        v.setLayoutParams(vParams);
        return new ViewHolder(v);
    }

    @Override
    // Replace the contents of a view (invoked by the layout manager)
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final ViewHolder vh = (ViewHolder) holder;
        final Context context = vh.mToggleButton.getContext();
        final ToggleAction toggle = (ToggleAction) mDataset.get(position);

        if (holder.getItemViewType() != ActionItemType.READONLY_ACTION) {
            vh.mToggleButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vh.mToggleButton.toggleState();
                    LocalBroadcastManager.getInstance(context)
                            .sendBroadcast(new Intent(WearableListenerActivity.ACTION_CHANGED)
                                    .putExtra(WearableListenerActivity.EXTRA_ACTIONDATA,
                                            JSONParser.serializer(new ToggleAction(toggle.getAction(), vh.mToggleButton.isActionEnabled()), Action.class)));
                }
            });
        } else {
            vh.mToggleButton.setOnClickListener(null);
        }

        vh.mToggleButton.setAction(toggle.getAction());
        if (toggle.isEnabled() != vh.mToggleButton.isActionEnabled())
            vh.mToggleButton.toggleState();

        switch (toggle.getAction()) {
            case WIFI:
            case BLUETOOTH:
            case TORCH:
            default:
                vh.mToggleButton.setToggleSuccessful(toggle.isActionSuccessful());
                break;
            case MOBILEDATA:
            case LOCATION:
                vh.mToggleButton.setToggleSuccessful(true);
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        int type = -1;

        switch (mDataset.get(position).getAction()) {
            case WIFI:
            case BLUETOOTH:
            case TORCH:
            default:
                type = ActionItemType.TOGGLE_ACTION;
                break;
            case MOBILEDATA:
            case LOCATION:
                type = ActionItemType.READONLY_ACTION;
                break;
        }

        return type;
    }

    @Override
    // Return the size of your dataset (invoked by the layout manager)
    public int getItemCount() {
        return mDataset.size();
    }

    public void updateToggle(ToggleAction action) {
        int idx = action.getAction().getValue();
        mDataset.set(idx, action);
        notifyItemChanged(idx);
    }
}
