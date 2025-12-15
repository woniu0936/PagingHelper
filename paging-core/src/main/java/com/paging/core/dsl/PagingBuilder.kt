package com.paging.core.dsl

import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.PagingHelper
import com.paging.core.engine.PagingEngine
import com.paging.core.engine.PagingListener
import com.paging.core.model.LoadState
import com.paging.core.model.PagingConfig
import com.paging.core.model.PagingResult
import com.paging.core.model.isError
import com.paging.core.source.AbstractPagingSource
import com.paging.core.source.PagingSource
import com.paging.core.ui.PagingBinder
import com.paging.core.ui.PagingFooterAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PagingBuilder<Key : Any, Value : Any>(
    private val recyclerView: RecyclerView,
    private val scope: CoroutineScope
) {
    // 必填项
    lateinit var adapter: RecyclerView.Adapter<*>
    lateinit var source: PagingSource<Key, Value>
    lateinit var footerAdapter: PagingFooterAdapter<*>

    // 选填项
    var config: PagingConfig = PagingConfig()

    // DSL 回调
    private var onStateChanged: ((LoadState) -> Unit)? = null
    private var onError: ((Throwable) -> Unit)? = null

    // 内部引用，用于支持 DSL 内调用 retry()
    private var engineRef: PagingEngine<Key, Value>? = null

    fun retry() = engineRef?.retry()

    // Java 桥接
    fun setSourceCompat(compatSource: AbstractPagingSource<Key, Value>) {
        this.source = PagingSource { key, size ->
            suspendCancellableCoroutine { cont ->
                compatSource.load(key, size, object : AbstractPagingSource.Callback<Key, Value> {
                    override fun onResult(data: List<Value>, nextKey: Key?) =
                        cont.resume(PagingResult(data, nextKey))

                    override fun onError(t: Throwable) =
                        cont.resumeWithException(t)
                })
            }
        }
    }

    fun onStateChanged(block: (LoadState) -> Unit) {
        onStateChanged = block
    }

    fun onError(block: (Throwable) -> Unit) {
        onError = block
    }

    internal fun build(): PagingHelper<Key, Value> {
        val binder = PagingBinder(recyclerView, config) { engineRef?.loadMore() }

        val listener = object : PagingListener<Value> {
            override fun onStateChanged(state: LoadState) {
                onStateChanged?.invoke(state)
                if (state.isError) onError?.invoke((state as LoadState.Error).error)
                binder.bindState(footerAdapter, state)
            }

            override fun onDataChanged(data: List<Value>) {
                if (adapter is ListAdapter<*, *>) {
                    @Suppress("UNCHECKED_CAST")
                    (adapter as ListAdapter<Value, *>).submitList(data)
                }
            }
        }

        val engine = PagingEngine(scope, source, config, listener)
        this.engineRef = engine // 关键：赋值引用

        binder.attach(adapter, footerAdapter)
        engine.refresh()

        return PagingHelper(engine)
    }
}