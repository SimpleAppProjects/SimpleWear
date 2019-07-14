package com.thewizrd.simplewear.controls;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.thewizrd.shared_resources.helpers.Actions;
import com.thewizrd.simplewear.R;

public class ToggleActionButton extends FloatingActionButton {
    private Drawable mIconOn;
    private Drawable mIconOff;
    private Context context;
    private boolean isEnabled;
    private Actions action;

    public ToggleActionButton(Context context) {
        super(context);
        initialize(context);
    }

    public ToggleActionButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public ToggleActionButton(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        this.context = context;

        mIconOff = context.getDrawable(R.drawable.ic_close_white_24dp);
        mIconOn = context.getDrawable(R.drawable.ic_check_white_24dp);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        setLayoutParams(params);
        setCustomSize((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 36, context.getResources().getDisplayMetrics()));
        //setUseCompatPadding(true);

        isEnabled = true;
        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimary)));
        setImageDrawable(mIconOn);

        this.setOnClickListener(onClickListener);
    }

    private View.OnClickListener onClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            toggleState();
        }
    };

    public void toggleState() {
        isEnabled = !isEnabled;
        setImageDrawable(isEnabled ? mIconOn : mIconOff);
        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimaryDark)));
    }

    public void resetState() {
        isEnabled = true;
        setImageDrawable(mIconOn);
        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.colorPrimary)));
    }

    public boolean isActionEnabled() {
        return isEnabled;
    }

    public void setIconOn(@NonNull Drawable drawable) {
        mIconOn = drawable;
    }

    public void setIconOff(@NonNull Drawable drawable) {
        mIconOff = drawable;
    }

    public void setAction(Actions action) {
        this.action = action;

        switch (action) {
            case WIFI:
                setIcons(context.getDrawable(R.drawable.ic_network_wifi_white_24dp), context.getDrawable(R.drawable.ic_signal_wifi_off_white_24dp));
                break;
            case BLUETOOTH:
                setIcons(context.getDrawable(R.drawable.ic_bluetooth_white_24dp), context.getDrawable(R.drawable.ic_bluetooth_disabled_white_24dp));
                break;
            case MOBILEDATA:
                setIcons(context.getDrawable(R.drawable.ic_network_cell_white_24dp), context.getDrawable(R.drawable.ic_signal_cellular_off_white_24dp));
                break;
            case LOCATION:
                setIcons(context.getDrawable(R.drawable.ic_location_on_white_24dp), context.getDrawable(R.drawable.ic_location_off_white_24dp));
                break;
            case TORCH:
                setIcons(context.getDrawable(R.drawable.ic_lightbulb_outline_white_24dp));
                break;
            case LOCKSCREEN:
                setIcons(context.getDrawable(R.drawable.ic_lock_outline_white_24dp));
                break;
        }
    }

    private void setIcons(Drawable drawableIcon) {
        setIconOn(drawableIcon);
        setIconOff(drawableIcon);
        setImageDrawable(isEnabled ? mIconOn : mIconOff);
    }

    private void setIcons(Drawable drawableIconOn, Drawable drawableIconOff) {
        setIconOn(drawableIconOn);
        setIconOff(drawableIconOff);
        setImageDrawable(isEnabled ? mIconOn : mIconOff);
    }

    public void setToggleSuccessful(boolean isSuccess) {
        if (isSuccess) {
            setSupportBackgroundTintList(isEnabled ?
                    ColorStateList.valueOf(context.getColor(R.color.colorPrimary)) :
                    ColorStateList.valueOf(context.getColor(R.color.black)));
        } else {
            // Revert state
            toggleState();
            setSupportBackgroundTintList(isEnabled ?
                    ColorStateList.valueOf(context.getColor(R.color.colorPrimary)) :
                    ColorStateList.valueOf(context.getColor(R.color.black)));
        }
    }
}
