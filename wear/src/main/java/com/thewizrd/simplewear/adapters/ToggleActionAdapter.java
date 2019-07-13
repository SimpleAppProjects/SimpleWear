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
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.ToggleActionButton;
import com.thewizrd.simplewear.WearableListenerActivity;

import java.util.ArrayList;
import java.util.List;

public class ToggleActionAdapter extends RecyclerView.Adapter {
    private List<ToggleAction> mDataset;

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

        v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int padding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 2, parent.getContext().getResources().getDisplayMetrics());
        v.setPaddingRelative(padding, padding, padding, padding);
        return new ViewHolder(v);
    }

    @Override
    // Replace the contents of a view (invoked by the layout manager)
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final ViewHolder vh = (ViewHolder) holder;
        final Context context = vh.mToggleButton.getContext();
        final ToggleAction toggle = mDataset.get(position);
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
        switch (toggle.getAction()) {
            case WIFI:
                vh.mToggleButton.setIcons(context.getDrawable(R.drawable.ic_network_wifi_white_24dp), context.getDrawable(R.drawable.ic_signal_wifi_off_white_24dp));
                if (toggle.isEnabled() != vh.mToggleButton.isActionEnabled())
                    vh.mToggleButton.toggleState();
                vh.mToggleButton.setToggleSuccessful(toggle.isActionSuccessful());
                break;
            case BLUETOOTH:
                vh.mToggleButton.setIcons(context.getDrawable(R.drawable.ic_bluetooth_white_24dp), context.getDrawable(R.drawable.ic_bluetooth_disabled_white_24dp));
                if (toggle.isEnabled() != vh.mToggleButton.isActionEnabled())
                    vh.mToggleButton.toggleState();
                vh.mToggleButton.setToggleSuccessful(toggle.isActionSuccessful());
                break;
            case MOBILEDATA:
                vh.mToggleButton.setIcons(context.getDrawable(R.drawable.ic_network_cell_white_24dp), context.getDrawable(R.drawable.ic_signal_cellular_off_white_24dp));
                if (toggle.isEnabled() != vh.mToggleButton.isActionEnabled())
                    vh.mToggleButton.toggleState();
                vh.mToggleButton.setToggleSuccessful(true);
                break;
        }
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
