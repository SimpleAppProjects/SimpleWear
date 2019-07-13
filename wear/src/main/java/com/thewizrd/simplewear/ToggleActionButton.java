package com.thewizrd.simplewear;

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

public class ToggleActionButton extends FloatingActionButton {
    private Drawable mIconOn;
    private Drawable mIconOff;
    private Context context;
    private boolean isEnabled;

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
        setSupportBackgroundTintList(ColorStateList.valueOf(context.getColor(R.color.black)));

        setImageDrawable(mIconOff);

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

    public boolean isActionEnabled() {
        return isEnabled;
    }

    public void setIconOn(@NonNull Drawable drawable) {
        mIconOn = drawable;
    }

    public void setIconOff(@NonNull Drawable drawable) {
        mIconOff = drawable;
    }

    public void setIcons(@NonNull Drawable drawableIconOn, @NonNull Drawable drawableIconOff) {
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
