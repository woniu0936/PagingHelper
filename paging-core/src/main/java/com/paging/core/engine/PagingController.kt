package com.paging.core.engine

/**
 * [Interface] 分页控制器契约
 * 定义了分页组件最基本的操作能力。
 * Library-Flow 将持有此接口，而不是具体的 Engine 实现。
 */
interface PagingController {
    fun refresh()
    fun loadMore()
    fun retry()
}