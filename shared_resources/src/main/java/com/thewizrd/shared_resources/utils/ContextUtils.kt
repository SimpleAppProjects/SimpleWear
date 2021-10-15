package com.thewizrd.shared_resources.utils

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Drawable
import android.util.TypedValue
import androidx.annotation.AnyRes
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt

object ContextUtils {
    fun Context.dpToPx(valueInDp: Float): Float {
        val metrics = this.resources.displayMetrics
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, valueInDp, metrics)
    }

    fun Context.isLargeTablet(): Boolean {
        return (this.resources.configuration.screenLayout
                and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun Context.isXLargeTablet(): Boolean {
        return (this.resources.configuration.screenLayout
                and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE
    }

    fun Context.isSmallestWidth(swdp: Int): Boolean {
        return this.resources.configuration.smallestScreenWidthDp >= swdp
    }

    fun Context.getOrientation(): Int {
        return this.resources.configuration.orientation
    }

    fun Context.getAttrDimension(@AttrRes resId: Int): Int {
        val value = TypedValue()
        this.theme.resolveAttribute(resId, value, true)
        return TypedValue.complexToDimensionPixelSize(
            value.data,
            this.resources.displayMetrics
        )
    }

    fun Context.getAttrValue(@AttrRes resId: Int): Int {
        val value = TypedValue()
        this.theme.resolveAttribute(resId, value, true)
        return value.data
    }

    @ColorInt
    fun Context.getAttrColor(@AttrRes resId: Int): Int {
        val array = this.theme.obtainStyledAttributes(intArrayOf(resId))
        @ColorInt val color = array.getColor(0, 0)
        array.recycle()
        return color
    }

    fun Context.getAttrColorStateList(@AttrRes resId: Int): ColorStateList? {
        val array = this.theme.obtainStyledAttributes(intArrayOf(resId))
        var color: ColorStateList? = null
        color = try {
            array.getColorStateList(0)
        } finally {
            array.recycle()
        }
        return color
    }

    fun Context.getAttrDrawable(@AttrRes resId: Int): Drawable? {
        val array = this.theme.obtainStyledAttributes(intArrayOf(resId))
        val drawable = array.getDrawable(0)
        array.recycle()
        return drawable
    }

    @AnyRes
    fun Context.getResourceId(@AttrRes resId: Int): Int {
        val array = this.theme.obtainStyledAttributes(intArrayOf(resId))
        val resourceId = array.getResourceId(0, 0)
        array.recycle()
        return resourceId
    }
}