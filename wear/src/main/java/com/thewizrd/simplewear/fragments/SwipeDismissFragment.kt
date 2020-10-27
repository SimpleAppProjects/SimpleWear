package com.thewizrd.simplewear.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.fragment.app.Fragment
import androidx.wear.widget.SwipeDismissFrameLayout
import com.thewizrd.simplewear.databinding.SwipeDismissLayoutBinding

open class SwipeDismissFragment : Fragment() {
    private lateinit var binding: SwipeDismissLayoutBinding
    private lateinit var swipeCallback: SwipeDismissFrameLayout.Callback

    @CallSuper
    @SuppressLint("RestrictedApi")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        binding = SwipeDismissLayoutBinding.inflate(inflater, container, false)

        binding.swipeLayout.isSwipeable = true
        swipeCallback = object : SwipeDismissFrameLayout.Callback() {
            override fun onDismissed(layout: SwipeDismissFrameLayout) {
                requireActivity().onBackPressed()
            }
        }
        binding.swipeLayout.addCallback(swipeCallback)

        return binding.swipeLayout
    }

    override fun onDestroyView() {
        binding.swipeLayout.removeCallback(swipeCallback)
        super.onDestroyView()
    }
}