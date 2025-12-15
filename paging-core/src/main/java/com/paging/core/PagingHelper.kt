package com.paging.core

import com.paging.core.engine.PagingEngine

/**
 * 对外暴露的控制手柄
 */
class PagingHelper<Key : Any, Value : Any> internal constructor(
    private val engine: PagingEngine<Key, Value>
) {
    fun refresh() = engine.refresh()
    fun loadMore() = engine.loadMore()
    fun retry() = engine.retry()
    val currentState get() = engine.currentState
}