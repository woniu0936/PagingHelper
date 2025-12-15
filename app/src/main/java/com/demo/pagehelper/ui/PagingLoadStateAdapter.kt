package com.demo.pagehelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.demo.pagehelper.R
import com.demo.pagehelper.model.LoadState

/**
 * Footer Adapter，负责展示 Loading / Error / End 状态，设计与 [ConcatAdapter] 配合使用。
 */
class PagingLoadStateAdapter(private val retry: () -> Unit) : RecyclerView.Adapter<PagingLoadStateAdapter.LoadStateViewHolder>() {

    var loadState: LoadState = LoadState.NotLoading
        set(value) {
            // 只处理影响 Footer 的状态
            val relevantState = value is LoadState.Append || value is LoadState.End
            val oldRelevantState = field is LoadState.Append || field is LoadState.End

            if (field != value) {
                field = value
                if (relevantState != oldRelevantState) {
                    if (relevantState) notifyItemInserted(0) else notifyItemRemoved(0)
                } else if (relevantState) {
                    notifyItemChanged(0)
                }
            }
        }

    var isDataEmpty: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (displayAsItem(loadState)) {
                    if (value) notifyItemRemoved(0) else notifyItemInserted(0)
                }
            }
        }

    private fun displayAsItem(state: LoadState) = state is LoadState.Append || state is LoadState.End
    override fun getItemCount(): Int = if (!isDataEmpty && displayAsItem(loadState)) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        LoadStateViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_footer, parent, false), retry)

    override fun onBindViewHolder(holder: LoadStateViewHolder, pos: Int) = holder.bind(loadState)

    class LoadStateViewHolder(itemView: View, retry: () -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val progress: ProgressBar = itemView.findViewById(R.id.footer_progress)
        private val retryButton: Button = itemView.findViewById(R.id.footer_retry_button)
        private val endText: TextView = itemView.findViewById(R.id.footer_end_text)

        init {
            retryButton.setOnClickListener { retry() }
        }

        fun bind(state: LoadState) {
            (itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
            progress.isVisible = state is LoadState.Append.Loading
            retryButton.isVisible = state is LoadState.Append.Error
            endText.isVisible = state is LoadState.End
        }
    }
}
