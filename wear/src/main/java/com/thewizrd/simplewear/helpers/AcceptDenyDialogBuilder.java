package com.thewizrd.simplewear.helpers;

import android.content.Context;
import android.content.DialogInterface;
import android.support.wearable.view.AcceptDenyDialog;

public class AcceptDenyDialogBuilder {
    private final Context mContext;
    private String message;
    private final DialogInterface.OnClickListener onClickListener;

    public AcceptDenyDialogBuilder(Context context, DialogInterface.OnClickListener onClickListener) {
        mContext = context;
        this.onClickListener = onClickListener;
    }

    private void apply(AcceptDenyDialog dialog) {
        dialog.setMessage(message);
        dialog.setPositiveButton(onClickListener);
        dialog.setNegativeButton(onClickListener);
    }

    public AcceptDenyDialogBuilder setMessage(int resId) {
        message = mContext.getString(resId);
        return this;
    }

    public AcceptDenyDialogBuilder setMessage(String message) {
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