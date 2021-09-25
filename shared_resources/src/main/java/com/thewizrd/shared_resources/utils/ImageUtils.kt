package com.thewizrd.shared_resources.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

object ImageUtils {
    @JvmStatic
    fun bitmapFromDrawable(context: Context, resDrawable: Int): Bitmap? {
        val drawable = ContextCompat.getDrawable(context, resDrawable) ?: return null
        return bitmapFromDrawable(drawable)
    }

    @JvmStatic
    fun bitmapFromDrawable(
        drawable: Drawable,
        maxWidth: Int = drawable.intrinsicWidth,
        maxHeight: Int = drawable.intrinsicHeight
    ): Bitmap {
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

    @JvmStatic
    fun createAssetFromBitmap(bitmap: Bitmap): Asset {
        val byteStream = ByteArrayOutputStream()
        return byteStream.use { stream ->
            bitmap.compress(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP
                }, 100, stream
            )
            Asset.createFromBytes(stream.toByteArray())
        }
    }

    suspend fun Bitmap.toAsset() = withContext(Dispatchers.IO) {
        val bmp = this@toAsset
        return@withContext createAssetFromBitmap(bmp)
    }

    suspend fun Bitmap.toByteArray() = withContext(Dispatchers.IO) {
        val byteStream = ByteArrayOutputStream()
        return@withContext byteStream.use { stream ->
            compress(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    Bitmap.CompressFormat.WEBP
                }, 100, stream
            )
            stream.toByteArray()
        }
    }

    suspend fun bitmapFromAssetStream(client: DataClient, asset: Asset?): Bitmap? {
        return withContext(Dispatchers.IO) {
            requireNotNull(asset) { "Asset must be non-null" }

            try {
                // convert asset into a file descriptor and block until it's ready
                val assetInputStream = client.getFdForAsset(asset).await()?.inputStream
                if (assetInputStream == null) {
                    Logger.writeLine(
                        Log.INFO,
                        "ImageUtils: bitmapFromAssetStream: Unknown asset requested"
                    )
                    return@withContext null
                }

                // decode the stream into a bitmap
                return@withContext assetInputStream.use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                Logger.writeLine(
                    Log.ERROR,
                    "ImageUtils: bitmapFromAssetStream: Failed to get asset"
                )
                return@withContext null
            }
        }
    }

    suspend fun ByteArray.toBitmap(): Bitmap? {
        return withContext(Dispatchers.IO) {
            try {
                this@toBitmap.inputStream().use {
                    BitmapFactory.decodeStream(it)
                }
            } catch (e: Exception) {
                Logger.writeLine(
                    Log.ERROR,
                    "ImageUtils: ByteArray.toBitmap: Error creating bitmap"
                )
                return@withContext null
            }
        }
    }
}