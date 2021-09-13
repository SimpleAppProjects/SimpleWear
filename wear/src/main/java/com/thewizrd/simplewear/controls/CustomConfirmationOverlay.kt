/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * ConfirmationOverlay.java
 * platform/frameworks/support
 * branch: pie-release
 */
package com.thewizrd.simplewear.controls

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.*
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.wear.R
import com.thewizrd.simplewear.utils.ResourcesUtils
import java.util.*

/**
 * Displays a full-screen confirmation animation with optional text and then hides it.
 *
 *
 * This is a lighter-weight version of [androidx.wear.activity.ConfirmationActivity]
 * and should be preferred when constructed from an [Activity].
 *
 *
 * Sample usage:
 *
 * <pre>
 * // Defaults to SUCCESS_ANIMATION
 * new CustomConfirmationOverlay().showOn(myActivity);
 *
 * new CustomConfirmationOverlay()
 * .setType(CustomConfirmationOverlay.OPEN_ON_PHONE_ANIMATION)
 * .setDuration(3000)
 * .setMessage("Opening...")
 * .setFinishedAnimationListener(new CustomConfirmationOverlay.OnAnimationFinishedListener() {
 * @Override
 * public void onAnimationFinished() {
 * // Finished animating and the content view has been removed from myActivity.
 * }
 * }).showOn(myActivity);
 *
 * // Default duration is [.DEFAULT_ANIMATION_DURATION_MS]
 * new CustomConfirmationOverlay()
 * .setType(CustomConfirmationOverlay.FAILURE_ANIMATION)
 * .setMessage("Failed")
 * .setFinishedAnimationListener(new CustomConfirmationOverlay.OnAnimationFinishedListener() {
 * @Override
 * public void onAnimationFinished() {
 * // Finished animating and the view has been removed from myView.getRootView().
 * }
 * }).showAbove(myView);
</pre> *
 */
class CustomConfirmationOverlay {
    companion object {
        /**
         * Default animation duration in ms.
         */
        const val DEFAULT_ANIMATION_DURATION_MS = 1000

        /**
         * [OverlayType] indicating the success animation overlay should be displayed.
         */
        const val SUCCESS_ANIMATION = 0

        /**
         * [OverlayType] indicating the failure overlay should be shown. The icon associated with
         * this type, unlike the others, does not animate.
         */
        const val FAILURE_ANIMATION = 1

        /**
         * [OverlayType] indicating the "Open on Phone" animation overlay should be displayed.
         */
        const val OPEN_ON_PHONE_ANIMATION = 2

        /**
         * [OverlayType] indicating a custom animation overlay should be displayed.
         */
        const val CUSTOM_ANIMATION = 3
    }

    /**
     * Interface for listeners to be notified when the [CustomConfirmationOverlay] animation has
     * finished and its [View] has been removed.
     */
    interface OnAnimationFinishedListener {
        /**
         * Called when the confirmation animation is finished.
         */
        fun onAnimationFinished()
    }

    /**
     * Types of animations to display in the overlay.
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(SUCCESS_ANIMATION, FAILURE_ANIMATION, OPEN_ON_PHONE_ANIMATION, CUSTOM_ANIMATION)
    annotation class OverlayType

    @OverlayType
    private var mType = SUCCESS_ANIMATION
    private var mDurationMillis = DEFAULT_ANIMATION_DURATION_MS
    private var mListener: OnAnimationFinishedListener? = null
    private var mMessage: CharSequence? = null
    @StringRes
    private var mMessageStringResId: Int? = null
    private var mOverlayView: View? = null
    private var mOverlayDrawable: Drawable? = null
    private var mIsShowing = false
    private var mCustomDrawable: Drawable? = null
    @DrawableRes
    private var mCustomDrawableResId: Int? = null
    private val mMainThreadHandler = Handler(Looper.getMainLooper())
    private val mHideRunnable = Runnable { hide() }

    /**
     * Sets a message which will be displayed at the same time as the animation.
     *
     * @return `this` object for method chaining.
     */
    fun setMessage(message: CharSequence?): CustomConfirmationOverlay {
        mMessage = message
        return this
    }

    /**
     * Sets the [OverlayType] which controls which animation is displayed.
     *
     * @return `this` object for method chaining.
     */
    fun setType(@OverlayType type: Int): CustomConfirmationOverlay {
        mType = type
        return this
    }

    /**
     * Sets the duration in milliseconds which controls how long the animation will be displayed.
     * Default duration is [.DEFAULT_ANIMATION_DURATION_MS].
     *
     * @return `this` object for method chaining.
     */
    fun setDuration(millis: Int): CustomConfirmationOverlay {
        mDurationMillis = millis
        return this
    }

    /**
     * Sets the [OnAnimationFinishedListener] which will be invoked once the overlay is no
     * longer visible.
     *
     * @return `this` object for method chaining.
     */
    fun setFinishedAnimationListener(
            listener: OnAnimationFinishedListener?): CustomConfirmationOverlay {
        mListener = listener
        return this
    }

    /**
     * Adds the overlay as a child of `view.getRootView()`, removing it when complete. While
     * it is shown, all touches will be intercepted to prevent accidental taps on obscured views.
     */
    @MainThread
    fun showAbove(view: View) {
        if (mIsShowing) {
            return
        }
        mIsShowing = true

        updateOverlayView(view.context)
        (view.rootView as ViewGroup).addView(mOverlayView)
        animateAndHideAfterDelay()
    }

    /**
     * Adds the overlay as a content view to the `activity`, removing it when complete. While
     * it is shown, all touches will be intercepted to prevent accidental taps on obscured views.
     */
    @MainThread
    fun showOn(activity: Activity) {
        if (mIsShowing) {
            return
        }
        mIsShowing = true

        updateOverlayView(activity)
        activity.window.addContentView(mOverlayView, mOverlayView!!.layoutParams)
        animateAndHideAfterDelay()
    }

    @MainThread
    private fun animateAndHideAfterDelay() {
        if (mOverlayDrawable is Animatable) {
            val animatable = mOverlayDrawable as Animatable
            animatable.start()
        }
        mMainThreadHandler.postDelayed(mHideRunnable, mDurationMillis.toLong())
    }

    /**
     * Starts a fadeout animation and removes the view once finished. This is invoked by [ ][.mHideRunnable] after [.mDurationMillis] milliseconds.
     *
     * @hide
     */
    @MainThread
    @VisibleForTesting
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    fun hide() {
        val fadeOut = AnimationUtils.loadAnimation(mOverlayView!!.context, android.R.anim.fade_out)
        fadeOut.setAnimationListener(
                object : Animation.AnimationListener {
                    override fun onAnimationStart(animation: Animation) {
                        mOverlayView!!.clearAnimation()
                    }

                    override fun onAnimationEnd(animation: Animation) {
                        (mOverlayView!!.parent as ViewGroup).removeView(mOverlayView)
                        mIsShowing = false
                        if (mListener != null) {
                            mListener!!.onAnimationFinished()
                        }
                    }

                    override fun onAnimationRepeat(animation: Animation) {}
                })
        mOverlayView!!.startAnimation(fadeOut)
    }

    @MainThread
    @SuppressLint("ClickableViewAccessibility")
    private fun updateOverlayView(context: Context) {
        if (mOverlayView == null) {
            mOverlayView = LayoutInflater.from(context).inflate(
                if (mType == CUSTOM_ANIMATION) {
                    com.thewizrd.simplewear.R.layout.ws_customoverlay_confirmation
                } else {
                    R.layout.ws_overlay_confirmation
                },
                null
            )
        }
        mOverlayView!!.setOnTouchListener { v, event -> true }
        mOverlayView!!.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        updateImageView(context, mOverlayView)
        updateMessageView(context, mOverlayView)
    }

    @MainThread
    private fun updateMessageView(context: Context, overlayView: View?) {
        val messageView =
            overlayView!!.findViewById<TextView>(R.id.wearable_support_confirmation_overlay_message)

        if (mMessage != null || (mMessageStringResId != null && mMessageStringResId != 0)) {
            val screenWidthPx = ResourcesUtils.getScreenWidthPx(context)
            val topMarginPx = ResourcesUtils.getFractionOfScreenPx(
                context, screenWidthPx, R.fraction.confirmation_overlay_margin_above_text
            )
            val sideMarginPx = ResourcesUtils.getFractionOfScreenPx(
                context, screenWidthPx, R.fraction.confirmation_overlay_margin_side
            )
            val layoutParams = messageView.layoutParams as MarginLayoutParams
            layoutParams.topMargin = topMarginPx
            layoutParams.leftMargin = sideMarginPx
            layoutParams.rightMargin = sideMarginPx
            messageView.layoutParams = layoutParams
            if (mMessageStringResId != null) {
                messageView.setText(mMessageStringResId!!)
            } else {
                messageView.text = mMessage
            }
            messageView.visibility = View.VISIBLE
        } else {
            messageView.visibility = View.GONE
        }
    }

    @MainThread
    private fun updateImageView(context: Context, overlayView: View?) {
        mOverlayDrawable = when (mType) {
            SUCCESS_ANIMATION -> ContextCompat.getDrawable(context,
                    R.drawable.generic_confirmation_animation)
            FAILURE_ANIMATION -> ContextCompat.getDrawable(context, R.drawable.ws_full_sad)
            OPEN_ON_PHONE_ANIMATION -> ContextCompat.getDrawable(context, R.drawable.ws_open_on_phone_animation)
            CUSTOM_ANIMATION -> {
                if (mCustomDrawableResId != null) {
                    ContextCompat.getDrawable(context, mCustomDrawableResId!!)
                } else {
                    checkNotNull(mCustomDrawable) { "Custom drawable is invalid" }
                    mCustomDrawable
                }
            }
            else -> {
                val errorMessage = String.format(Locale.US, "Invalid ConfirmationOverlay type [%d]", mType)
                throw IllegalStateException(errorMessage)
            }
        }

        val imageView =
            overlayView!!.findViewById<ImageView>(R.id.wearable_support_confirmation_overlay_image)
        imageView.setImageDrawable(mOverlayDrawable)
        if (imageView.layoutParams is ConstraintLayout.LayoutParams) {
            val lp = imageView.layoutParams as ConstraintLayout.LayoutParams
            if (mMessage.isNullOrBlank()) lp.verticalBias = 0.5f
            imageView.layoutParams = lp
        }
    }

    /**
     * Sets a message which will be displayed at the same time as the animation.
     *
     * @return `this` object for method chaining.
     */
    fun setMessage(@StringRes resId: Int?): CustomConfirmationOverlay {
        mMessageStringResId = resId
        return this
    }

    /**
     * Sets the custom image drawable which will be displayed.
     * This will be used if type is set to CUSTOM_ANIMATION
     *
     * @return `this` object for method chaining.
     */
    fun setCustomDrawable(@DrawableRes resId: Int?): CustomConfirmationOverlay {
        mCustomDrawableResId = resId
        return this
    }

    /**
     * Sets the custom image drawable which will be displayed.
     * This will be used if type is set to CUSTOM_ANIMATION
     *
     * @return `this` object for method chaining.
     */
    fun setCustomDrawable(customDrawable: Drawable?): CustomConfirmationOverlay {
        mCustomDrawable = customDrawable
        return this
    }
}