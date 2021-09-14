package com.thewizrd.simplewear

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.wear.widget.SwipeDismissFrameLayout
import com.thewizrd.simplewear.adapters.MediaPlayerListAdapter
import com.thewizrd.simplewear.databinding.MediaplayerfilterListBinding
import com.thewizrd.simplewear.helpers.SimpleRecyclerViewAdapterObserver
import com.thewizrd.simplewear.preferences.Settings
import com.thewizrd.simplewear.viewmodels.MediaPlayerListViewModel

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
                binding.progressBar.visibility = View.GONE
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