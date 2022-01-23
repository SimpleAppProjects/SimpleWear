package com.thewizrd.simplewear.preferences

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.*
import com.thewizrd.shared_resources.actions.Actions
import com.thewizrd.shared_resources.helpers.ListAdapterOnClickInterface
import com.thewizrd.shared_resources.utils.ContextUtils.dpToPx
import com.thewizrd.simplewear.controls.ActionButtonViewModel
import com.thewizrd.simplewear.controls.WearChipButton
import com.thewizrd.simplewear.databinding.DialogAddactionBinding
import com.thewizrd.simplewear.helpers.SpacerItemDecoration

class AddActionDialogBuilder(private val context: Context, private val actionsList: List<Actions>) {
    private lateinit var adapter: ActionsListAdapter
    private var onActionSelectedListener: OnActionSelectedListener? = null
    private lateinit var binding: DialogAddactionBinding

    interface OnActionSelectedListener {
        fun onActionSelected(action: Actions)
    }

    private fun createView(): View {
        binding = DialogAddactionBinding.inflate(LayoutInflater.from(context))

        adapter = ActionsListAdapter(ActionButtonViewModel.DIFF_CALLBACK)

        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addItemDecoration(
            SpacerItemDecoration(
                verticalSpace = context.dpToPx(4f).toInt()
            )
        )

        adapter.submitList(actionsList.map {
            ActionButtonViewModel.getViewModelFromAction(it)
        })

        return binding.root
    }

    fun setOnActionSelectedListener(listener: OnActionSelectedListener?): AddActionDialogBuilder {
        this.onActionSelectedListener = listener
        return this
    }

    fun show() {
        val dialog = AlertDialog.Builder(context)
            .setCancelable(true)
            .setView(createView())
            .create()

        adapter.setOnClickListener(object : ListAdapterOnClickInterface<ActionButtonViewModel> {
            override fun onClick(view: View, item: ActionButtonViewModel) {
                onActionSelectedListener?.onActionSelected(item.actionType)
                dialog.dismiss()
            }
        })

        dialog.show()
    }

    private class ActionsListAdapter :
        ListAdapter<ActionButtonViewModel, ActionsListAdapter.ViewHolder> {
        private var onClickListener: ListAdapterOnClickInterface<ActionButtonViewModel>? = null

        constructor(diffCallback: DiffUtil.ItemCallback<ActionButtonViewModel>) : super(diffCallback)
        protected constructor(config: AsyncDifferConfig<ActionButtonViewModel>) : super(config)

        fun setOnClickListener(onClickListener: ListAdapterOnClickInterface<ActionButtonViewModel>?) {
            this.onClickListener = onClickListener
        }

        inner class ViewHolder(private val button: WearChipButton) :
            RecyclerView.ViewHolder(button) {
            fun bind(model: ActionButtonViewModel) {
                button.setPrimaryText(model.actionLabel)
                button.setIconResource(model.drawableID)
                itemView.setOnClickListener {
                    onClickListener?.onClick(it, model)
                }
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            // create a new view
            val v = WearChipButton(parent.context).apply {
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }

            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(getItem(position))
        }
    }
}