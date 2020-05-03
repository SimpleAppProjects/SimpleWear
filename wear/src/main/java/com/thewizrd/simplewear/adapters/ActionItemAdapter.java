package com.thewizrd.simplewear.adapters;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.wear.widget.WearableRecyclerView;

import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.shared_resources.helpers.MultiChoiceAction;
import com.thewizrd.shared_resources.helpers.NormalAction;
import com.thewizrd.shared_resources.helpers.ToggleAction;
import com.thewizrd.shared_resources.helpers.ValueAction;
import com.thewizrd.shared_resources.helpers.ValueDirection;
import com.thewizrd.shared_resources.utils.StringUtils;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.controls.ActionButton;
import com.thewizrd.simplewear.controls.ActionButtonViewModel;

import java.util.ArrayList;
import java.util.List;

public class ActionItemAdapter extends RecyclerView.Adapter {
    private static class ActionItemType {
        final static int ACTION = 0;
        final static int TOGGLE_ACTION = 1;
        final static int VALUE_ACTION = 2;
        final static int READONLY_ACTION = 3;
        final static int MULTICHOICE_ACTION = 4;
    }

    private List<ActionButtonViewModel> mDataset;
    private Activity mActivityContext;
    private boolean mItemsClickable = true;

    // Provide a reference to the views for each data item
    // Complex data items may need more than one view per item, and
    // you provide access to all the views for a data item in a view holder
    class ViewHolder extends RecyclerView.ViewHolder {
        ActionButton mButton;

        ViewHolder(ActionButton v) {
            super(v);
            mButton = v;
        }
    }

    public boolean isItemsClickable() {
        return mItemsClickable;
    }

    public void setItemsClickable(boolean itemsClickable) {
        this.mItemsClickable = itemsClickable;
    }

    // Provide a suitable constructor (depends on the kind of dataset)
    public ActionItemAdapter(Activity activity) {
        mDataset = new ArrayList<>();
        mActivityContext = activity;

        for (Actions action : Actions.values()) {
            switch (action) {
                case WIFI:
                case BLUETOOTH:
                case MOBILEDATA:
                case LOCATION:
                case TORCH:
                    mDataset.add(new ActionButtonViewModel(new ToggleAction(action, true)));
                    break;
                case LOCKSCREEN:
                case MUSICPLAYBACK:
                case SLEEPTIMER:
                    mDataset.add(new ActionButtonViewModel(new NormalAction(action)));
                    break;
                case VOLUME:
                    mDataset.add(new ActionButtonViewModel(new ValueAction(action, ValueDirection.UP)));
                    break;
                case DONOTDISTURB:
                case RINGER:
                    mDataset.add(new ActionButtonViewModel(new MultiChoiceAction(action)));
                    break;
            }
        }
    }

    @SuppressLint("NewApi")
    @NonNull
    @Override
    // Create new views (invoked by the layout manager)
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // create a new view
        ActionButton v = new ActionButton(parent.getContext());
        RecyclerView recyclerView = (WearableRecyclerView) parent;
        RecyclerView.LayoutManager viewLayoutMgr = recyclerView.getLayoutManager();

        RecyclerView.LayoutParams vParams = new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int innerPadding = parent.getContext().getResources().getDimensionPixelSize(R.dimen.inner_layout_padding);

        if (viewLayoutMgr instanceof GridLayoutManager) {
            v.setExpanded(false);

            GridLayoutManager gLM = (GridLayoutManager) viewLayoutMgr;

            int horizPadding = 0;
            try {
                int spanCount = gLM.getSpanCount();
                int viewWidth = parent.getMeasuredWidth() - innerPadding * 2;
                int colWidth = (viewWidth / spanCount);
                horizPadding = colWidth - v.getFabCustomSize();
            } catch (Exception ignored) {
            }

            int vertPadding = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 6, parent.getContext().getResources().getDisplayMetrics());
            v.setPaddingRelative(0, 0, 0, 0);
            recyclerView.setPaddingRelative(innerPadding, 0, innerPadding, 0);
            vParams.setMargins(horizPadding / 2, vertPadding, horizPadding / 2, vertPadding);
        } else if (viewLayoutMgr instanceof LinearLayoutManager) {
            v.setExpanded(true);

            v.setPaddingRelative(innerPadding, 0, innerPadding, 0);
            recyclerView.setPaddingRelative(0, 0, 0, 0);
        }

        v.setLayoutParams(vParams);
        return new ViewHolder(v);
    }
    @Override
    // Replace the contents of a view (invoked by the layout manager)
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, final int position) {
        // - get element from your dataset at this position
        // - replace the contents of the view with that element
        final ViewHolder vh = (ViewHolder) holder;
        final ActionButtonViewModel actionVM = mDataset.get(position);

        vh.mButton.updateButton(actionVM);

        if (holder.getItemViewType() != ActionItemType.READONLY_ACTION) {
            vh.mButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (!mItemsClickable) return;

                    actionVM.onClick(mActivityContext);
                    notifyItemChanged(position);
                }
            });
        } else {
            vh.mButton.setOnClickListener(null);
        }

        if (vh.mButton.isExpanded()) {
            vh.mButton.setOnLongClickListener(null);
        } else {
            vh.mButton.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    String text = actionVM.getActionLabel();
                    if (!StringUtils.isNullOrWhitespace(actionVM.getStateLabel())) {
                        text = String.format("%s: %s", text, actionVM.getStateLabel());
                    }
                    Toast.makeText(v.getContext(), text, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
    }

    @Override
    public int getItemViewType(int position) {
        int type = -1;

        switch (mDataset.get(position).getActionType()) {
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
            case LOCKSCREEN:
            case MUSICPLAYBACK:
            case SLEEPTIMER:
                type = ActionItemType.ACTION;
                break;
            case VOLUME:
                type = ActionItemType.VALUE_ACTION;
                break;
            case DONOTDISTURB:
            case RINGER:
                type = ActionItemType.MULTICHOICE_ACTION;
                break;
        }

        return type;
    }

    @Override
    // Return the size of your dataset (invoked by the layout manager)
    public int getItemCount() {
        return mDataset.size();
    }

    public void updateButton(ActionButtonViewModel action) {
        int idx = action.getActionType().getValue();
        mDataset.set(idx, action);
        notifyItemChanged(idx);
    }
}
