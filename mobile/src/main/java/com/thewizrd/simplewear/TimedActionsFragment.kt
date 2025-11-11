package com.thewizrd.simplewear

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.transition.TransitionManager
import com.google.android.material.transition.MaterialFade
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.simplewear.adapters.SpacerAdapter
import com.thewizrd.simplewear.adapters.TimedActionsAdapter
import com.thewizrd.simplewear.databinding.FragmentTimedActionsBinding
import com.thewizrd.simplewear.helpers.AlarmStateManager
import com.thewizrd.simplewear.helpers.PhoneStatusHelper
import com.thewizrd.simplewear.helpers.SwipeToDeleteCallback
import com.thewizrd.simplewear.wearable.WearableWorker

class TimedActionsFragment : Fragment(), SharedPreferences.OnSharedPreferenceChangeListener {

    private lateinit var binding: FragmentTimedActionsBinding

    private lateinit var actionAdapter: TimedActionsAdapter
    private lateinit var selectionTracker: SelectionTracker<Long>
    private lateinit var alarmStateManager: AlarmStateManager

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTimedActionsBinding.inflate(inflater, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            binding.recyclerView.updateLayoutParams<CoordinatorLayout.LayoutParams> {
                val sysBarInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                bottomMargin = sysBarInsets.top + sysBarInsets.bottom
            }

            insets
        }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        actionAdapter = TimedActionsAdapter()
        actionAdapter.setHasStableIds(true)
        binding.recyclerView.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setStableIdMode(ConcatAdapter.Config.StableIdMode.SHARED_STABLE_IDS)
                .build(),
            actionAdapter, SpacerAdapter(requireContext().dpToPx(64f).toInt())
        )

        binding.actionDelete.setOnClickListener {
            selectionTracker.selection.toList().forEach { itemId ->
                deleteAction(actionId = itemId)
            }
        }

        binding.actionSelectAll.setOnClickListener {
            selectionTracker.setItemsSelected(
                actionAdapter.currentList.map { it.action.actionType.value.toLong() },
                true
            )
        }

        val keyProvider = StableIdKeyProvider(binding.recyclerView)
        val sBuilder = SelectionTracker.Builder(
            "action-select", binding.recyclerView, keyProvider,
            object : ItemDetailsLookup<Long>() {
                override fun getItemDetails(e: MotionEvent): ItemDetails<Long>? {
                    val view = binding.recyclerView.findChildViewUnder(e.x, e.y)
                    if (view != null) {
                        val holder = binding.recyclerView.getChildViewHolder(view)

                        if (holder is TimedActionsAdapter.TimedActionViewHolder) {
                            return holder.itemDetails
                        }
                    }

                    return null
                }
            },
            StorageStrategy.createLongStorage()
        )
        selectionTracker = sBuilder
            .withSelectionPredicate(SelectionPredicates.createSelectAnything())
            .build()
        actionAdapter.selectionTracker = selectionTracker

        onBackPressedCallback = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                selectionTracker.clearSelection()
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(onBackPressedCallback)

        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            override fun onSelectionChanged() {
                if (!selectionTracker.hasSelection()) {
                    onBackPressedCallback.isEnabled = false
                    animateToolbar(false)
                } else {
                    onBackPressedCallback.isEnabled = true
                    animateToolbar(true)
                }

                binding.selectionCount.text = selectionTracker.selection.size().toString()
            }

            @SuppressLint("RestrictedApi")
            override fun onSelectionCleared() {
                onBackPressedCallback.isEnabled = false
                animateToolbar(false)
            }
        })

        val swipeToDeleteHandler = object : SwipeToDeleteCallback(requireContext()) {
            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is TimedActionsAdapter.TimedActionViewHolder) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun getDragDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return if (viewHolder is TimedActionsAdapter.TimedActionViewHolder) {
                    super.getDragDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                if (viewHolder is TimedActionsAdapter.TimedActionViewHolder) {
                    val ctx = viewHolder.itemView.context
                    deleteAction(ctx, viewHolder.itemId)
                }
            }
        }
        ItemTouchHelper(swipeToDeleteHandler).run {
            attachToRecyclerView(binding.recyclerView)
        }

        alarmStateManager = AlarmStateManager(requireContext())

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        updateActionList()
        alarmStateManager.preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onDestroyView() {
        alarmStateManager.preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onDestroyView()
    }

    private fun updateActionList() {
        val list = alarmStateManager.getAlarms().values.sortedBy { it?.timeInMillis }.toList()
        binding.emptyText.isVisible = list.isEmpty()
        actionAdapter.submitList(list)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (Actions.entries.any { it.name == key }) {
            updateActionList()
        }
    }

    private fun deleteAction(ctx: Context = requireContext(), actionId: Long) {
        val action = Actions.valueOf(actionId.toInt())
        PhoneStatusHelper.removedScheduledTimedAction(ctx, action)
        WearableWorker.enqueueAction(ctx, WearableWorker.ACTION_SENDTIMEDACTIONSUPDATE)
        selectionTracker.deselect(actionId)
    }

    private fun animateToolbar(visible: Boolean) {
        TransitionManager.beginDelayedTransition(binding.root, MaterialFade().apply {
            duration = if (visible) 150 else 84
        })
        binding.floatingToolbar.isVisible = visible
    }
}