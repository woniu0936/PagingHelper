package com.paging.flow

import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.model.LoadState
import com.paging.core.model.PagingConfig
import com.paging.core.ui.PagingFooterAdapter

/**
 * [DSL Entry] Flow 版本的 setupPaging。
 *
 * @param flowPaging ViewModel 中创建好的 FlowPaging 对象
 */
fun <Key : Any, Value : Any> RecyclerView.setupPaging(
    lifecycleOwner: LifecycleOwner,
    flowPaging: FlowPaging<Key, Value>,
    block: FlowPagingBuilder<Key, Value>.() -> Unit
) {
    val builder = FlowPagingBuilder(this, lifecycleOwner, flowPaging)
    builder.block()
    builder.build()
}

/**
 * [DSL Context] 构建器上下文
 */
class FlowPagingBuilder<Key : Any, Value : Any>(
    private val recyclerView: RecyclerView,
    private val lifecycleOwner: LifecycleOwner,
    private val paging: FlowPaging<Key, Value>
) {
    // 必填项
    lateinit var adapter: androidx.recyclerview.widget.ListAdapter<Value, *>
    lateinit var footerAdapter: PagingFooterAdapter<*>

    // 选填项
    var config: PagingConfig = PagingConfig()

    // 额外回调
    private var onStateChanged: ((LoadState) -> Unit)? = null

    /**
     * 添加额外的状态监听 (例如处理 Toast, ProgressBar)
     */
    fun onStateChanged(block: (LoadState) -> Unit) {
        onStateChanged = block
    }

    internal fun build() {
        // 1. 物理绑定 (Binder)
        val binder = recyclerView.bindController(paging, config)
        binder.attach(adapter, footerAdapter)

        // 2. 数据绑定
        paging.bindDataTo(lifecycleOwner, adapter)

        // 3. 状态绑定 (Footer)
        paging.bindStateTo(lifecycleOwner, binder, footerAdapter)

        // 4. 额外监听
        if (onStateChanged != null) {
            paging.loadState.collectOnLifecycle(lifecycleOwner) { state ->
                onStateChanged?.invoke(state)
            }
        }
    }
}