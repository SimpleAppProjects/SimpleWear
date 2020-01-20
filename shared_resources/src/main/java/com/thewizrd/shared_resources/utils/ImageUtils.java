package com.thewizrd.shared_resources.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Tasks;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class ImageUtils {
    public static Bitmap bitmapFromDrawable(Context context, int resDrawable) {
        Drawable drawable = ContextCompat.getDrawable(context, resDrawable);
        if (drawable == null) {
            return null;
        }

        return bitmapFromDrawable(drawable);
    }

    public static Bitmap bitmapFromDrawable(Context context, Drawable drawable) {
        return bitmapFromDrawable(drawable);
    }

    private static Bitmap bitmapFromDrawable(Drawable drawable) {
        Bitmap bitmap;

        if (drawable.getIntrinsicHeight() <= 0 || drawable.getIntrinsicWidth() <= 0) {
            bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            bitmap = Bitmap.createBitmap(drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        }

        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    public static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }

    public static Bitmap bitmapFromAssetStream(final DataClient client, final Asset asset) {
        return new AsyncTask<Bitmap>().await(new Callable<Bitmap>() {
            @Override
            public Bitmap call() {
                if (asset == null) {
                    throw new IllegalArgumentException("Asset must be non-null");
                }

                try {
                    // convert asset into a file descriptor and block until it's ready
                    InputStream assetInputStream = Tasks.await(client.getFdForAsset(asset)).getInputStream();
                    if (assetInputStream == null) {
                        Logger.writeLine(Log.INFO, "ImageUtils: bitmapFromAssetStream: Unknown asset requested");
                        return null;
                    }

                    // decode the stream into a bitmap
                    return BitmapFactory.decodeStream(assetInputStream);
                } catch (ExecutionException | InterruptedException e) {
                    Logger.writeLine(Log.ERROR, "ImageUtils: bitmapFromAssetStream: Failed to get asset");
                    return null;
                }
            }
        });
    }
}