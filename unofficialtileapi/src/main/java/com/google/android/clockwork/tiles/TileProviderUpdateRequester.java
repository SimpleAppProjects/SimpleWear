package com.google.android.clockwork.tiles;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

@TargetApi(24)
public class TileProviderUpdateRequester {
    private static final String UPDATE_REQUEST_RECEIVER_PACKAGE = "com.google.android.wearable.app";
    public static final String ACTION_REQUEST_UPDATE = "android.support.wearable.tiles.ACTION_REQUEST_UPDATE";
    public static final String ACTION_REQUEST_UPDATE_ALL = "android.support.wearable.tiles.ACTION_REQUEST_UPDATE_ALL";
    public static final String EXTRA_PENDING_INTENT = "android.support.wearable.tiles.EXTRA_PENDING_INTENT";
    public static final String EXTRA_PROVIDER_COMPONENT = "android.support.wearable.tiles.EXTRA_PROVIDER_COMPONENT";
    public static final String EXTRA_TILE_IDS = "android.support.wearable.tiles.EXTRA_TILE_IDS";
    private final Context mContext;
    private final ComponentName mProviderComponent;

    public TileProviderUpdateRequester(Context context, ComponentName providerComponent) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null");
        } else if (providerComponent == null) {
            throw new IllegalArgumentException("ProviderComponent cannot be null");
        } else {
            this.mContext = context;
            this.mProviderComponent = providerComponent;
        }
    }

    public final Intent buildIntentToRequestUpdateAll() {
        Intent intent = new Intent(ACTION_REQUEST_UPDATE_ALL);
        intent.setPackage(UPDATE_REQUEST_RECEIVER_PACKAGE);
        intent.putExtra(EXTRA_PROVIDER_COMPONENT, this.mProviderComponent);
        intent.putExtra(EXTRA_PENDING_INTENT, PendingIntent.getActivity(this.mContext, 0, new Intent(""), 0));
        return intent;
    }

    public final void requestUpdate(int... tileIds) {
        Intent intent = new Intent(ACTION_REQUEST_UPDATE);
        intent.setPackage(UPDATE_REQUEST_RECEIVER_PACKAGE);
        intent.putExtra(EXTRA_PROVIDER_COMPONENT, this.mProviderComponent);
        intent.putExtra(EXTRA_TILE_IDS, tileIds);
        intent.putExtra(EXTRA_PENDING_INTENT, PendingIntent.getActivity(this.mContext, 0, new Intent(""), 0));
        this.mContext.sendBroadcast(intent);
    }

    public final void requestUpdateAll() {
        Intent intent = buildIntentToRequestUpdateAll();
        this.mContext.sendBroadcast(intent);
    }
}