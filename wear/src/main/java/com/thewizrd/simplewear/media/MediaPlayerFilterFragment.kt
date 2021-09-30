package com.thewizrd.simplewear.media

import android.os.Bundle
import android.view.*
import androidx.core.view.InputDeviceCompat
import androidx.core.view.MotionEventCompat
import androidx.core.view.ViewConfigurationCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.wear.widget.SwipeDismissFrameLayout
import com.thewizrd.simplewear.adapters.MediaPlayerListAdapter
import com.thewizrd.simplewear.databinding.MediaplayerfilterListBinding
import com.thewizrd.simplewear.helpers.SimpleRecyclerViewAdapterObserver
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel
import kotlin.math.roundToInt

class MediaPlayerFilterFragment : DialogFragment() {
    private lateinit var binding: MediaplayerfilterListBinding
    private lateinit var mAdapter: MediaPlayerListAdapter

    private val viewModel: MediaPlayerListViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = MediaplayerfilterListBinding.inflate(inflater, container, false)

        val filteredApps = Settings.getMusicPlayersFilter()
        binding.playerList.adapter = MediaPlayerListAdapter(filteredApps).also {
            mAdapter = it
        }

        binding.playerList.setHasFixedSize(false)

        mAdapter.registerAdapterDataObserver(object : SimpleRecyclerViewAdapterObserver() {
            override fun onChanged() {
                super.onChanged()
                mAdapter.unregisterAdapterDataObserver(this)
                binding.progressBar.hide()
                binding.scrollViewContent.visibility = View.VISIBLE
            }
        })

        binding.root.addCallback(object : SwipeDismissFrameLayout.Callback() {
            override fun onDismissed(layout: SwipeDismissFrameLayout?) {
                dismissFragment()
            }
        })

        binding.clearButton.setOnClickListener {
            mAdapter.clearSelections()
        }

        binding.confirmButton.setOnClickListener {
            dismissFragment()
        }

        binding.root.setOnGenericMotionListener { view, event ->
            if (event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDeviceCompat.SOURCE_ROTARY_ENCODER)) {
                // Don't forget the negation here
                val delta = -event.getAxisValue(MotionEventCompat.AXIS_SCROLL) *
                        ViewConfigurationCompat.getScaledVerticalScrollFactor(
                            ViewConfiguration.get(view.context), view.context
                        )

                // Swap these axes if you want to do horizontal scrolling instead
                binding.scrollView.scrollBy(0, delta.roundToInt())

                return@setOnGenericMotionListener true
            }
            false
        }

        return binding.root
    }

    fun dismissFragment() {
        // Update filtered apps
        Settings.setMusicPlayersFilter(mAdapter.getSelectedItems())
        viewModel.filteredAppsList.postValue(mAdapter.getSelectedItems())
        dismiss()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.scrollView.requestFocus()

        viewModel.mediaAppsList.observe(viewLifecycleOwner, {
            mAdapter.submitList(it.toList())
        })
    }
}