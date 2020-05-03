package com.thewizrd.simplewear.sleeptimer;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.ViewModelStoreOwner;
import androidx.recyclerview.widget.AsyncListDiffer;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.thewizrd.shared_resources.helpers.RecyclerOnClickListenerInterface;
import com.thewizrd.simplewear.R;
import com.thewizrd.simplewear.controls.MusicPlayerViewModel;
import com.thewizrd.simplewear.databinding.MusicplayerItemSleeptimerBinding;

import java.util.List;
import java.util.Objects;

public class PlayerListAdapter extends RecyclerView.Adapter<PlayerListAdapter.ViewHolder> {
    private AsyncListDiffer<MusicPlayerViewModel> mDiffer;
    private int mCheckedPosition = RecyclerView.NO_POSITION;
    private RecyclerOnClickListenerInterface onClickListener = null;

    private SelectedPlayerViewModel selectedPlayer;

    public PlayerListAdapter(ViewModelStoreOwner owner) {
        mDiffer = new AsyncListDiffer<>(this, diffCallback);
        selectedPlayer = new ViewModelProvider(owner, new ViewModelProvider.NewInstanceFactory())
                .get(SelectedPlayerViewModel.class);
    }

    private static class Payload {
        static int RADIOBUTTON_UPDATE = 0;
    }

    public void setOnClickListener(RecyclerOnClickListenerInterface listener) {
        this.onClickListener = listener;
    }

    private DiffUtil.ItemCallback<MusicPlayerViewModel> diffCallback = new DiffUtil.ItemCallback<MusicPlayerViewModel>() {
        @Override
        public boolean areItemsTheSame(@NonNull MusicPlayerViewModel oldItem, @NonNull MusicPlayerViewModel newItem) {
            return Objects.equals(oldItem.getPackageName(), newItem.getPackageName()) && Objects.equals(oldItem.getActivityName(), newItem.getActivityName());
        }

        @Override
        public boolean areContentsTheSame(@NonNull MusicPlayerViewModel oldItem, @NonNull MusicPlayerViewModel newItem) {
            return Objects.equals(oldItem, newItem);
        }
    };

    class ViewHolder extends RecyclerView.ViewHolder {
        private View itemView;
        private MusicplayerItemSleeptimerBinding binding;

        ViewHolder(View itemView) {
            super(itemView);
            this.itemView = itemView;
            binding = MusicplayerItemSleeptimerBinding.bind(itemView);
        }

        void bindModel(MusicPlayerViewModel viewModel) {
            if (viewModel.getBitmapIcon() == null) {
                binding.playerIcon.setImageResource(R.drawable.ic_play_circle_filled_white_24dp);
            } else {
                binding.playerIcon.setImageBitmap(viewModel.getBitmapIcon());
            }
            binding.playerName.setText(viewModel.getAppLabel());
        }

        private void updateRadioButtom() {
            if (mCheckedPosition == RecyclerView.NO_POSITION) {
                binding.radioButton.setChecked(false);
            } else {
                if (mCheckedPosition == getAdapterPosition()) {
                    binding.radioButton.setChecked(true);
                } else {
                    binding.radioButton.setChecked(false);
                }
            }

            View.OnClickListener clickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int oldPosition = mCheckedPosition;
                    if (mCheckedPosition != getAdapterPosition()) {
                        mCheckedPosition = getAdapterPosition();
                        notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE);
                        notifyItemChanged(mCheckedPosition, Payload.RADIOBUTTON_UPDATE);
                    } else {
                        // uncheck
                        mCheckedPosition = RecyclerView.NO_POSITION;
                        notifyItemChanged(oldPosition, Payload.RADIOBUTTON_UPDATE);
                    }

                    if (getSelectedItem() != null) {
                        selectedPlayer.setKey(getSelectedItem().getKey());
                    } else {
                        selectedPlayer.setKey(null);
                    }

                    if (onClickListener != null)
                        onClickListener.onClick(itemView, getAdapterPosition());
                }
            };
            itemView.setOnClickListener(clickListener);
            binding.radioButton.setOnClickListener(clickListener);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.musicplayer_item_sleeptimer, parent, false);
        int innerPadding = parent.getContext().getResources().getDimensionPixelSize(R.dimen.inner_layout_padding);
        ViewCompat.setPaddingRelative(view, innerPadding, 0, innerPadding, 0);
        return new ViewHolder(view);
    }

    @Override
    public int getItemCount() {
        return mDiffer.getCurrentList().size();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bindModel(mDiffer.getCurrentList().get(position));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        boolean radioBtnUpdateOnly;
        if (!payloads.isEmpty()) {
            radioBtnUpdateOnly = Objects.equals(payloads.get(0), Payload.RADIOBUTTON_UPDATE);
        } else {
            radioBtnUpdateOnly = false;
        }

        if (!radioBtnUpdateOnly) {
            super.onBindViewHolder(holder, position, payloads);
        }

        holder.updateRadioButtom();
    }

    void updateItems(List<MusicPlayerViewModel> dataset) {
        String currentPref = selectedPlayer.getKey().getValue();
        MusicPlayerViewModel item = null;

        if (dataset != null) {
            for (MusicPlayerViewModel it : dataset) {
                if (it.getKey() != null && Objects.equals(it.getKey(), currentPref)) {
                    item = it;
                    break;
                }
            }
        }

        if (item != null) {
            mCheckedPosition = dataset.indexOf(item);
        } else {
            mCheckedPosition = RecyclerView.NO_POSITION;
        }

        mDiffer.submitList(dataset);
    }

    public MusicPlayerViewModel getSelectedItem() {
        if (mCheckedPosition != RecyclerView.NO_POSITION) {
            return mDiffer.getCurrentList().get(mCheckedPosition);
        }

        return null;
    }
}