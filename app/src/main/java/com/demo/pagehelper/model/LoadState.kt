package com.demo.pagehelper.model

/**
 * [终极修正] 列表加载状态机，将 Refresh 和 Append 的状态完全分离。
 * 这从根本上解决了状态混淆和UI显示冲突的问题。
 */
sealed class LoadState {
    /** 刷新操作的状态。通常影响整个屏幕（如骨架屏、下拉刷新圈）。 */
    sealed class Refresh : LoadState() {
        /** 正在刷新中。 */
        object Loading : Refresh()
        /** 刷新失败。 */
        data class Error(val error: PagingError) : Refresh()
    }

    /** 加载更多操作的状态。通常只影响列表末尾的 Footer。 */
    sealed class Append : LoadState() {
        /** 正在加载更多。 */
        object Loading : Append()
        /** 加载更多失败。 */
        data class Error(val error: PagingError) : Append()
    }

    /** 已加载全部数据，没有更多了。这是一个终端状态。 */
    object End : LoadState()

    /** 初始状态或操作成功后的空闲状态。 */
    object NotLoading : LoadState()
}
