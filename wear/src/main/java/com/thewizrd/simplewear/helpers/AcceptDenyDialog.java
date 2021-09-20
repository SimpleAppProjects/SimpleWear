package com.thewizrd.simplewear.helpers;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;

import com.thewizrd.simplewear.databinding.AcceptDenyDialogBinding;

public class AcceptDenyDialog extends Dialog {
    private final AcceptDenyDialogBinding binding;
    protected OnClickListener mPositiveButtonListener;
    protected OnClickListener mNegativeButtonListener;
    private final View.OnClickListener mButtonHandler;

    public AcceptDenyDialog(@NonNull Context context) {
        this(context, 0);
    }

    public AcceptDenyDialog(@NonNull Context context, @StyleRes int themeResId) {
        super(context, themeResId);
        mButtonHandler = new android.view.View.OnClickListener() {
            public void onClick(View v) {
                if (v == binding.buttonPositive && AcceptDenyDialog.this.mPositiveButtonListener != null) {
                    AcceptDenyDialog.this.mPositiveButtonListener.onClick(AcceptDenyDialog.this, DialogInterface.BUTTON_POSITIVE);
                } else if (v == binding.buttonNegative && AcceptDenyDialog.this.mNegativeButtonListener != null) {
                    AcceptDenyDialog.this.mNegativeButtonListener.onClick(AcceptDenyDialog.this, DialogInterface.BUTTON_NEGATIVE);
                }

                AcceptDenyDialog.this.dismiss();
            }
        };
        binding = AcceptDenyDialogBinding.inflate(LayoutInflater.from(getContext()));
        setContentView(binding.getRoot());
        binding.buttonPositive.setOnClickListener(mButtonHandler);
        binding.buttonNegative.setOnClickListener(mButtonHandler);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding.title.getRootView().requestFocus();
    }

    @Nullable
    public View getButton(int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                return binding.buttonPositive;
            case DialogInterface.BUTTON_NEGATIVE:
                return binding.buttonNegative;
            default:
                return null;
        }
    }

    public void setIcon(@Nullable Drawable icon) {
        binding.icon.setVisibility(icon == null ? View.GONE : View.VISIBLE);
        binding.icon.setImageDrawable(icon);
    }

    public void setIcon(@DrawableRes int resId) {
        binding.icon.setVisibility(resId == 0 ? View.GONE : View.VISIBLE);
        binding.icon.setImageResource(resId);
    }

    public void setMessage(@Nullable CharSequence message) {
        binding.message.setText(message);
        binding.message.setVisibility(message == null ? View.GONE : View.VISIBLE);
    }

    public void setMessage(@StringRes int resId) {
        binding.message.setText(resId);
        binding.message.setVisibility(resId == 0 ? View.GONE : View.VISIBLE);
    }

    public void setTitle(@Nullable CharSequence message) {
        binding.title.setText(message);
    }

    public void setTitle(@StringRes int resId) {
        binding.title.setText(resId);
    }

    public void setButton(int which, OnClickListener listener) {
        switch (which) {
            case DialogInterface.BUTTON_NEGATIVE:
                this.mNegativeButtonListener = listener;
                break;
            case DialogInterface.BUTTON_POSITIVE:
                this.mPositiveButtonListener = listener;
                break;
            default:
                return;
        }

        binding.spacer.setVisibility(this.mPositiveButtonListener != null && this.mNegativeButtonListener != null ? View.INVISIBLE : View.GONE);
        binding.buttonPositive.setVisibility(this.mPositiveButtonListener == null ? View.GONE : View.VISIBLE);
        binding.buttonNegative.setVisibility(this.mNegativeButtonListener == null ? View.GONE : View.VISIBLE);
        binding.buttonPanel.setVisibility(this.mPositiveButtonListener == null && this.mNegativeButtonListener == null ? View.GONE : View.VISIBLE);
    }

    public void setPositiveButton(OnClickListener listener) {
        this.setButton(DialogInterface.BUTTON_POSITIVE, listener);
    }

    public void setNegativeButton(OnClickListener listener) {
        this.setButton(DialogInterface.BUTTON_NEGATIVE, listener);
    }

    public static class Builder {
        private final Context mContext;
        private CharSequence message;
        private final DialogInterface.OnClickListener onClickListener;

        public Builder(Context context, DialogInterface.OnClickListener onClickListener) {
            mContext = context;
            this.onClickListener = onClickListener;
        }

        private void apply(AcceptDenyDialog dialog) {
            dialog.setMessage(message);
            dialog.setPositiveButton(onClickListener);
            dialog.setNegativeButton(onClickListener);
        }

        public Builder setMessage(@StringRes int resId) {
            message = mContext.getString(resId);
            return this;
        }

        public Builder setMessage(CharSequence message) {
            this.message = message;
            return this;
        }

        public AcceptDenyDialog create() {
            AcceptDenyDialog dialog = new AcceptDenyDialog(mContext);
            dialog.create();
            apply(dialog);
            return dialog;
        }

        public AcceptDenyDialog show() {
            AcceptDenyDialog dialog = this.create();
            dialog.show();
            return dialog;
        }
    }
}