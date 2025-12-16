package com.paging.flow

import android.util.Log
import com.paging.core.BuildConfig
import com.paging.core.engine.PagingController
import com.paging.core.engine.PagingEngine
import com.paging.core.engine.PagingListener
import com.paging.core.model.LoadState
import com.paging.core.model.PagingConfig
import com.paging.core.source.PagingSource
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.plus

/**
 * [Top-Tier Design] 响应式分页容器。
 *
 * 它不仅仅是一个 Engine 的包装，更是一个支持数据流变换的管道。
 * 核心特性：
 * 1. **不可变性**：数据流 (data) 和状态流 (loadState) 是只读的 StateFlow。
 * 2. **变换能力**：支持 .map() 操作符，实现 DTO 到 UI Model 的转换。
 * 3. **控制权分离**：无论数据如何变换，refresh/retry 始终指向原始的数据源控制器。
 */
class FlowPaging<Key : Any, Value : Any> private constructor(
    val data: StateFlow<List<Value>>,
    val loadState: StateFlow<LoadState>,
    private val controller: PagingController
) {
    // --- 1. 对外暴露控制器 (委托给 Controller) ---
    fun refresh() = controller.refresh()
    fun loadMore() = controller.loadMore()
    fun retry() = controller.retry()

    /**
     * [DSL Support] 给 setupPaging DSL 提供的内部访问器
     */
    internal fun getController() = controller

    companion object {
        /**
         * [Factory] 创建一个原始的 FlowPaging (Root)
         * 通常在 ViewModel 初始化时调用。
         */
        fun <Key : Any, Value : Any> create(
            scope: CoroutineScope,
            source: PagingSource<Key, Value>,
            config: PagingConfig = PagingConfig()
        ): FlowPaging<Key, Value> {
            // 1. 创建底层 StateFlow 数据源
            val _loadState = MutableStateFlow<LoadState>(LoadState.NotLoading)
            val _data = MutableStateFlow<List<Value>>(emptyList())

            val handler = CoroutineExceptionHandler { _, exception ->
                if (BuildConfig.DEBUG) {
                    Log.e("FlowPaging", "🔥 UNCAUGHT COROUTINE EXCEPTION 🔥", exception)
                }
            }
            val safeScope = scope + handler

            // 2. 连接 Core Engine
            val listener = object : PagingListener<Value> {
                override fun onStateChanged(state: LoadState) { _loadState.value = state }
                override fun onDataChanged(data: List<Value>) { _data.value = data }
            }
            val engine = PagingEngine(safeScope, source, config, listener)

            // 3. 自动启动
            engine.refresh()

            return FlowPaging(
                data = _data.asStateFlow(),
                loadState = _loadState.asStateFlow(),
                controller = engine
            )
        }
    }

    // ==========================================
    //       Top-Tier Operators (顶级操作符)
    // ==========================================

    /**
     * [Map Operator] 数据转换
     *
     * 将 FlowPaging<Key, T> 转换为 FlowPaging<Key, R>。
     * 这是一个非常强大的功能，允许你在 ViewModel 层将后端数据转换为 UI 数据，
     * 而 View 层只需要消费转换后的 FlowPaging。
     *
     * @param scope 用于维持转换后的 StateFlow 热流的协程作用域 (通常是 viewModelScope)
     * @param transform 转换函数
     */
    fun <R : Any> map(
        scope: CoroutineScope,
        transform: (Value) -> R
    ): FlowPaging<Key, R> {
        // 1. 转换数据流
        val mappedDataFlow = this.data
            .map { list -> list.map(transform) }
            .stateIn(
                scope = scope,
                started = SharingStarted.Eagerly, // 保持热流
                initialValue = this.data.value.map(transform)
            )

        // 2. 返回新的 FlowPaging 实例
        // 注意：loadState 和 controller 保持不变，指向同一个源
        return FlowPaging(
            data = mappedDataFlow,
            loadState = this.loadState,
            controller = this.controller
        )
    }
}