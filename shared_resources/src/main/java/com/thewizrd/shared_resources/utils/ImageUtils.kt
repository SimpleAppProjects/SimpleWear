package com.thewizrd.shared_resources.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import com.thewizrd.shared_resources.tasks.AsyncTask
import com.thewizrd.shared_resources.utils.Logger.writeLine
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutionException

object ImageUtils {
    fun bitmapFromDrawable(context: Context?, resDrawable: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context!!, resDrawable)
                ?: return null
        return bitmapFromDrawable(drawable)
    }

    fun bitmapFromDrawable(drawable: Drawable, maxWidth: Int = drawable.intrinsicWidth, maxHeight: Int = drawable.intrinsicHeight): Bitmap {
        val bitmap: Bitmap
        var maxWidth = maxWidth
        var maxHeight = maxHeight

        if (maxWidth <= 0 || maxHeight <= 0) {
            maxWidth = 1
            maxHeight = 1
        }

        bitmap = if (drawable.intrinsicHeight <= 0 || drawable.intrinsicWidth <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(
                    Math.min(drawable.intrinsicWidth, maxWidth),
                    Math.min(drawable.intrinsicHeight, maxHeight),
                    Bitmap.Config.ARGB_8888
            )
        }

        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }

    fun createAssetFromBitmap(bitmap: Bitmap): Asset {
        val byteStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.WEBP, 100, byteStream)
        return Asset.createFromBytes(byteStream.toByteArray())
    }

    fun bitmapFromAssetStream(client: DataClient, asset: Asset?): Bitmap? {
        return AsyncTask.await<Bitmap> {
            requireNotNull(asset) { "Asset must be non-null" }

            try {
                // convert asset into a file descriptor and block until it's ready
                val assetInputStream = Tasks.await(client.getFdForAsset(asset)).inputStream
                if (assetInputStream == null) {
                    writeLine(Log.INFO, "ImageUtils: bitmapFromAssetStream: Unknown asset requested")
                    return@await null
                }

                // decode the stream into a bitmap
                return@await BitmapFactory.decodeStream(assetInputStream)
            } catch (e: ExecutionException) {
                writeLine(Log.ERROR, "ImageUtils: bitmapFromAssetStream: Failed to get asset")
                return@await null
            } catch (e: InterruptedException) {
                writeLine(Log.ERROR, "ImageUtils: bitmapFromAssetStream: Failed to get asset")
                return@await null
            }
        }
    }
}