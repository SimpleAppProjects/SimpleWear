package com.thewizrd.simplewear.preferences

import android.content.DialogInterface
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.simplewear.R
import com.thewizrd.simplewear.activities.AppCompatLiteActivity
import com.thewizrd.simplewear.adapters.AddButtonAdapter
import com.thewizrd.simplewear.adapters.TileActionAdapter
import com.thewizrd.simplewear.databinding.LayoutDashboardConfigBinding
import com.thewizrd.simplewear.helpers.AcceptDenyDialog
import com.thewizrd.simplewear.helpers.TileActionsItemTouchCallback

class DashboardConfigActivity : AppCompatLiteActivity() {
    companion object {
        private val MAX_BUTTONS = Actions.values().size
        private val DEFAULT_TILES = Actions.values().toList()
    }

    private lateinit var binding: LayoutDashboardConfigBinding
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var actionAdapter: TileActionAdapter
    private lateinit var addButtonAdapter: AddButtonAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutDashboardConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.tileGridLayout.layoutManager = GridLayoutManager(this, 3)

        actionAdapter = TileActionAdapter()
        addButtonAdapter = AddButtonAdapter()

        binding.tileGridLayout.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder()
                .setIsolateViewTypes(false)
                .build(),
            actionAdapter
        ).also {
            concatAdapter = it
        }

        itemTouchHelper = ItemTouchHelper(TileActionsItemTouchCallback(actionAdapter))
        itemTouchHelper.attachToRecyclerView(binding.tileGridLayout)

        actionAdapter.onLongClickListener = {
            itemTouchHelper.startDrag(it)
        }

        actionAdapter.onListChanged = {
            if (it.size >= MAX_BUTTONS) {
                concatAdapter.removeAdapter(addButtonAdapter)
            } else {
                concatAdapter.addAdapter(addButtonAdapter)
            }
        }

        val config = Settings.getDashboardConfig()

        config?.let {
            actionAdapter.submitActions(it)
        } ?: run {
            actionAdapter.submitActions(DEFAULT_TILES)
        }

        addButtonAdapter.setOnClickListener {
            val allowedActions = Actions.values().toMutableList()
            // Remove current actions
            allowedActions.removeAll(actionAdapter.getActions())

            AddActionDialogBuilder(this, allowedActions)
                .setOnActionSelectedListener(object :
                    AddActionDialogBuilder.OnActionSelectedListener {
                    override fun onActionSelected(action: Actions) {
                        actionAdapter.addAction(action)
                    }
                })
                .show()
        }

        binding.resetButton.setOnClickListener {
            AcceptDenyDialog.Builder(this) { _, which ->
                when (which) {
                    DialogInterface.BUTTON_POSITIVE -> {
                        actionAdapter.submitActions(DEFAULT_TILES)
                        Settings.setDashboardConfig(null)
                    }
                }
            }
                .setMessage(R.string.message_reset_to_default)
                .show()
        }

        binding.saveButton.setOnClickListener {
            val currentList = actionAdapter.getActions()
            Settings.setDashboardConfig(currentList)

            // Close activity
            finish()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) {
            val currSelection = actionAdapter.getSelection()
            if (currSelection != null) {
                val r = Rect().also {
                    currSelection.getGlobalVisibleRect(it)
                }
                if (!r.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    actionAdapter.clearSelection()
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }
}