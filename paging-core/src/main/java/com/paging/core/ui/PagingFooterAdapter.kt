package com.paging.core.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.paging.core.model.LoadState

abstract class PagingFooterAdapter<VH : RecyclerView.ViewHolder>(
    private val retryCallback: () -> Unit
) : RecyclerView.Adapter<VH>() {

    var state: LoadState = LoadState.NotLoading
        set(value) {
            if (field != value) {
                val oldShow = shouldDisplay(field)
                val newShow = shouldDisplay(value)
                field = value
                if (oldShow != newShow) {
                    if (newShow) notifyItemInserted(0) else notifyItemRemoved(0)
                } else if (newShow) {
                    notifyItemChanged(0)
                }
            }
        }

    protected open fun shouldDisplay(state: LoadState) =
        state is LoadState.Loading || state is LoadState.Error || state is LoadState.End

    final override fun getItemCount() = if (shouldDisplay(state)) 1 else 0

    final override fun onBindViewHolder(holder: VH, position: Int) {
        val params = holder.itemView.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) params.isFullSpan = true
        onBindFooter(holder, state, retryCallback)
    }

    abstract fun onBindFooter(holder: VH, state: LoadState, retry: () -> Unit)
    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
}