package com.paging.core.model

/**
 * 加载状态：清晰区分 刷新(Refresh) 和 追加(Append)
 */
sealed class LoadState {
    object NotLoading : LoadState()
    object End : LoadState()
    data class Loading(val isRefresh: Boolean) : LoadState()
    data class Error(val error: Throwable, val isRefresh: Boolean) : LoadState()
}

/**
 * 语法糖：是否正在加载 (无论是刷新还是加载更多)
 */
val LoadState.isLoading: Boolean
    get() = this is LoadState.Loading

/**
 * 语法糖：是否是刷新操作
 */
val LoadState.isRefresh: Boolean
    get() = (this is LoadState.Loading && this.isRefresh) || (this is LoadState.Error && this.isRefresh)

/**
 * 语法糖：是否是错误状态
 */
val LoadState.isError: Boolean
    get() = this is LoadState.Error

/**
 * 语法糖：是否已结束
 */
val LoadState.isEnd: Boolean
    get() = this is LoadState.End

/**
 * 提取错误异常，非错误状态返回 null
 */
val LoadState.errorOrNull: Throwable?
    get() = (this as? LoadState.Error)?.error

/**
 * 仅在加载更多失败时执行 block
 */
inline fun LoadState.onAppendError(block: (Throwable) -> Unit) {
    if (this is LoadState.Error && !this.isRefresh) {
        block(this.error)
    }
}