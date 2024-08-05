package com.thewizrd.simplewear

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.ActionMode
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionPredicates
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.thewizrd.shared_resources.actions.Actions
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

    private var actionMode: ActionMode? = null
    private val actionModeCallback = object : ActionMode.Callback {
        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            activity?.menuInflater?.inflate(R.menu.selectable_list, menu)
            actionMode = mode
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return false
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return when (item?.itemId) {
                R.id.action_selectAll -> {
                    selectionTracker.setItemsSelected(
                        actionAdapter.currentList.map { it.action.actionType.value.toLong() },
                        true
                    )
                    true
                }

                R.id.action_delete -> {
                    selectionTracker.selection.forEach { itemId ->
                        deleteAction(actionId = itemId)
                    }
                    true
                }

                else -> false
            }
        }

        override fun onDestroyActionMode(mode: ActionMode?) {
            selectionTracker.clearSelection()
            actionMode = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentTimedActionsBinding.inflate(inflater, container, false)

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())

        actionAdapter = TimedActionsAdapter()
        actionAdapter.setHasStableIds(true)
        binding.recyclerView.adapter = actionAdapter

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
                if (selectionTracker.selection.isEmpty) {
                    onBackPressedCallback.isEnabled = false
                    actionMode?.finish()
                } else {
                    onBackPressedCallback.isEnabled = true
                    if (actionMode == null) {
                        activity?.startActionMode(actionModeCallback)
                    }
                }

                actionMode?.title = selectionTracker.selection.size().toString()
            }

            override fun onSelectionCleared() {
                onBackPressedCallback.isEnabled = false
                actionMode?.finish()
            }
        })

        val swipeToDeleteHandler = object : SwipeToDeleteCallback(requireContext()) {
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
    }
}