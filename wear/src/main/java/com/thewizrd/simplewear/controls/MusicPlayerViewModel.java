package com.thewizrd.simplewear.controls;

import android.graphics.Bitmap;

public class MusicPlayerViewModel {
    private Bitmap mBitmapIcon;
    private String mAppLabel;
    private String mPackageName;
    private String mActivityName;

    public Bitmap getBitmapIcon() {
        return mBitmapIcon;
    }

    public void setBitmapIcon(Bitmap bitmapIcon) {
        this.mBitmapIcon = bitmapIcon;
    }

    public String getAppLabel() {
        return mAppLabel;
    }

    public void setAppLabel(String label) {
        this.mAppLabel = label;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        this.mPackageName = packageName;
    }

    public String getActivityName() {
        return mActivityName;
    }

    public void setActivityName(String activityName) {
        this.mActivityName = activityName;
    }
}
