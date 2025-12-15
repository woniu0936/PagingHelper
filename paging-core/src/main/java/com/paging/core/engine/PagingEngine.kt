package com.paging.core.engine

import androidx.annotation.RestrictTo
import com.paging.core.model.LoadState
import com.paging.core.model.PagingConfig
import com.paging.core.model.isEnd
import com.paging.core.model.isError
import com.paging.core.model.isLoading
import com.paging.core.source.PagingSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
interface PagingListener<Value> {
    fun onStateChanged(state: LoadState)
    fun onDataChanged(data: List<Value>)
}

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PagingEngine<Key : Any, Value : Any>(
    private val scope: CoroutineScope,
    private val source: PagingSource<Key, Value>,
    private val config: PagingConfig,
    private val listener: PagingListener<Value>
) : PagingController {
    private val mutex = Mutex()
    private var currentKey: Key? = null
    private val currentList = ArrayList<Value>()
    private var loadJob: Job? = null

    @Volatile
    var currentState: LoadState = LoadState.NotLoading
        private set

    override fun refresh() {
        loadJob?.cancel()
        loadJob = scope.launch { loadInternal(isRefresh = true) }
    }

    override fun loadMore() {
        if (currentState.isLoading || currentState.isEnd || currentState.isError) return
        loadJob = scope.launch { loadInternal(isRefresh = false) }
    }

    override fun retry() {
        if (currentState is LoadState.Error) {
            if ((currentState as LoadState.Error).isRefresh) refresh() else loadMore()
        }
    }

    private suspend fun loadInternal(isRefresh: Boolean) {
        mutex.withLock {
            val key = if (isRefresh) null else currentKey
            updateState(LoadState.Loading(isRefresh))

            try {
                val result = source.load(key, config.pageSize)
                if (isRefresh) currentList.clear()
                currentList.addAll(result.data)
                currentKey = result.nextKey

                listener.onDataChanged(ArrayList(currentList))
                updateState(if (currentKey == null) LoadState.End else LoadState.NotLoading)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                updateState(LoadState.Error(e, isRefresh))
            }
        }
    }

    private fun updateState(state: LoadState) {
        currentState = state
        listener.onStateChanged(state)
    }
}
