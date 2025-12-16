package com.paging.flow

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.BuildConfig
import com.paging.core.model.PagingConfig
import com.paging.core.ui.PagingBinder
import com.paging.core.ui.PagingFooterAdapter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * [Foundation] 自动收集 Flow 数据，并感知生命周期
 */
fun <T> Flow<T>.collectOnLifecycle(
    lifecycleOwner: LifecycleOwner,
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend (T) -> Unit
) {
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(state) {
            this@collectOnLifecycle.collectLatest(action)
        }
    }
}

/**
 * [Foundation] 绑定控制器与 UI，返回 Binder 对象
 */
fun RecyclerView.bindController(
    paging: FlowPaging<*, *>,
    config: PagingConfig
): PagingBinder {
    // 实例化 Core 模块的 Binder，连接滚动事件
    return PagingBinder(this, config) {
        paging.loadMore()
    }
}

/**
 * [Foundation] 绑定数据流到 Adapter
 */
fun <T : Any> FlowPaging<*, T>.bindDataTo(
    lifecycleOwner: LifecycleOwner,
    adapter: ListAdapter<T, *>
) {
    this.data.collectOnLifecycle(lifecycleOwner) { list ->
        if (BuildConfig.DEBUG) {
            Log.d("PagingDSL", "Receive Data: size=${list.size}")
        }
        adapter.submitList(list)
    }
}

/**
 * [Foundation] 绑定状态流到 Footer
 */
fun FlowPaging<*, *>.bindStateTo(
    lifecycleOwner: LifecycleOwner,
    binder: PagingBinder,
    footerAdapter: PagingFooterAdapter<*>
) {
    this.loadState.collectOnLifecycle(lifecycleOwner) { state ->
        binder.bindState(footerAdapter, state)
    }
}