package com.thewizrd.simplewear.controls;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.thewizrd.simplewear.R;

public class MusicPlayerItem extends LinearLayout {
    private ImageView mPlayerIcon;
    private TextView mPlayerName;

    public MusicPlayerItem(Context context) {
        super(context);
        initialize(context);
    }

    public MusicPlayerItem(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public MusicPlayerItem(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    public MusicPlayerItem(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context);
    }

    private void initialize(Context context) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View view = inflater.inflate(R.layout.musicplayer_item, this);

        mPlayerIcon = view.findViewById(R.id.player_icon);
        mPlayerName = view.findViewById(R.id.player_name);
    }

    public void updateItem(MusicPlayerViewModel viewModel) {
        if (viewModel.getBitmapIcon() != null)
            mPlayerIcon.setImageBitmap(viewModel.getBitmapIcon());
        else
            mPlayerIcon.setImageResource(R.drawable.ic_play_circle_filled_white_24dp);
        mPlayerName.setText(viewModel.getAppLabel());
    }
}
