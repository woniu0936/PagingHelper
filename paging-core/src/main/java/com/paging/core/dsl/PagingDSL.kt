package com.paging.core.dsl

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.PagingHelper
import kotlinx.coroutines.CoroutineScope

// 顶级扩展：Lifecycle 绑定
fun <Key : Any, Value : Any> RecyclerView.setupPaging(
    lifecycleOwner: LifecycleOwner,
    block: PagingBuilder<Key, Value>.() -> Unit
): PagingHelper<Key, Value> {
    val builder = PagingBuilder<Key, Value>(this, lifecycleOwner.lifecycleScope)
    builder.block()
    return builder.build()
}

// 顶级扩展：自定义 Scope
fun <Key : Any, Value : Any> RecyclerView.setupPaging(
    scope: CoroutineScope,
    block: PagingBuilder<Key, Value>.() -> Unit
): PagingHelper<Key, Value> {
    val builder = PagingBuilder<Key, Value>(this, scope)
    builder.block()
    return builder.build()
}