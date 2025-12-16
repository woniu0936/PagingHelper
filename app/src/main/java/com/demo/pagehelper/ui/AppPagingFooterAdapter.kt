package com.demo.pagehelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.demo.pagehelper.R
import com.paging.core.model.LoadState
import com.paging.core.ui.PagingFooterAdapter

class AppPagingFooterAdapter(
    retry: () -> Unit
) : PagingFooterAdapter<AppPagingFooterAdapter.LoadStateViewHolder>(retry) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LoadStateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_footer, parent, false)
        return LoadStateViewHolder(view)
    }

    override fun onBindFooter(holder: LoadStateViewHolder, state: LoadState, retry: () -> Unit) {
        holder.bind(state, retry)
    }

    class LoadStateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val progress: ProgressBar = itemView.findViewById(R.id.footer_progress)
        private val retryButton: Button = itemView.findViewById(R.id.footer_retry_button)
        private val endText: TextView = itemView.findViewById(R.id.footer_end_text)

        fun bind(state: LoadState, retry: () -> Unit) {
            progress.isVisible = state is LoadState.Loading
            retryButton.isVisible = state is LoadState.Error
            endText.isVisible = state is LoadState.End
            retryButton.setOnClickListener { retry() }
        }
    }
}