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

    protected open fun shouldDisplay(state: LoadState): Boolean {
        return when (state) {
            // 1. 如果是 Loading 状态
            is LoadState.Loading -> !state.isRefresh // 只有 "非刷新" (即加载更多) 时才显示

            // 2. 如果是 Error 状态
            is LoadState.Error -> !state.isRefresh   // 只有 "非刷新" (即加载更多) 出错时才显示 Footer 的重试按钮
            // 刷新出错通常由 SwipeRefreshLayout 收起或弹 Toast 处理

            // 3. 如果是 End 状态 (没有更多)
            is LoadState.End -> true // 总是显示 (或者你可以加逻辑：如果列表为空则不显示)

            // 4. 其他 (NotLoading)
            else -> false
        }
    }

    final override fun getItemCount() = if (shouldDisplay(state)) 1 else 0

    final override fun onBindViewHolder(holder: VH, position: Int) {
        val params = holder.itemView.layoutParams
        if (params is StaggeredGridLayoutManager.LayoutParams) params.isFullSpan = true
        onBindFooter(holder, state, retryCallback)
    }

    abstract fun onBindFooter(holder: VH, state: LoadState, retry: () -> Unit)
    abstract override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH
}