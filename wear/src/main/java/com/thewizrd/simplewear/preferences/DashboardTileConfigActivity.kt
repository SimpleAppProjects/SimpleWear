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
import com.thewizrd.simplewear.databinding.LayoutTileDashboardConfigBinding
import com.thewizrd.simplewear.helpers.AcceptDenyDialog
import com.thewizrd.simplewear.helpers.TileActionsItemTouchCallback

class DashboardTileConfigActivity : AppCompatLiteActivity() {
    companion object {
        internal const val MAX_BUTTONS = 6
        internal val DEFAULT_TILES by lazy {
            listOf(
                Actions.WIFI, Actions.BLUETOOTH, Actions.LOCKSCREEN,
                Actions.DONOTDISTURB, Actions.RINGER, Actions.TORCH
            )
        }
    }

    private lateinit var binding: LayoutTileDashboardConfigBinding
    private lateinit var concatAdapter: ConcatAdapter
    private lateinit var actionAdapter: TileActionAdapter
    private lateinit var addButtonAdapter: AddButtonAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = LayoutTileDashboardConfigBinding.inflate(layoutInflater)
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

        val config = Settings.getDashboardTileConfig()

        config?.let {
            actionAdapter.submitActions(it)
        } ?: run {
            actionAdapter.submitActions(DEFAULT_TILES)
        }

        addButtonAdapter.setOnClickListener {
            val allowedActions = Actions.values().toMutableList()
            // Remove current actions
            allowedActions.removeAll(actionAdapter.getActions())
            // Remove other actions which need an activity
            allowedActions.removeIf {
                return@removeIf when (it) {
                    Actions.VOLUME,
                    Actions.MUSICPLAYBACK,
                    Actions.SLEEPTIMER,
                    Actions.APPS,
                    Actions.PHONE,
                    Actions.BRIGHTNESS -> true
                    else -> false
                }
            }

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
                        Settings.setDashboardTileConfig(null)
                    }
                }
            }
                .setMessage(R.string.message_reset_to_default)
                .show()
        }

        binding.saveButton.setOnClickListener {
            val currentList = actionAdapter.getActions()
            Settings.setDashboardTileConfig(currentList)

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