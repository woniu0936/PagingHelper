package com.paging.core.engine

import android.util.Log
import androidx.annotation.RestrictTo
import com.paging.core.BuildConfig
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

private const val TAG = "PagingEngine"

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
        // 自动加载（滚动触发）必须拦截 Error 状态，防止无限重试
        if (currentState.isLoading || currentState.isEnd || currentState.isError) return
        loadJob = scope.launch { loadInternal(isRefresh = false) }
    }

    override fun retry() {
        val state = currentState
        if (state is LoadState.Error) {
            if (state.isRefresh) {
                refresh()
            } else {
                // [关键修复]
                // 不要调用 loadMore()，因为它会检查 isError 并拦截。
                // 这里是手动重试，权限更高，直接发起加载协程。
                loadJob = scope.launch {
                    loadInternal(isRefresh = false)
                }
            }
        }
    }

    private suspend fun loadInternal(isRefresh: Boolean) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, ">>> loadInternal Start: isRefresh=$isRefresh")
        }
        mutex.withLock {
            val key = if (isRefresh) null else currentKey
            Log.d(TAG, "    Target Key: $key")
            updateState(LoadState.Loading(isRefresh))

            try {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "    Calling source.load()...")
                }
                val result = source.load(key, config.pageSize)
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "    Source loaded success. Data size: ${result.data.size}")
                }
                if (isRefresh) currentList.clear()
                currentList.addAll(result.data)
                currentKey = result.nextKey

                listener.onDataChanged(ArrayList(currentList))
                updateState(if (currentKey == null) LoadState.End else LoadState.NotLoading)
            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Log.e(TAG, "!!! CATCH EXCEPTION !!!", e)
                }
                if (e is CancellationException) {
                    if (BuildConfig.DEBUG) {
                        Log.w(TAG, "    Job Cancelled")
                    }
                    throw e
                }
                updateState(LoadState.Error(e, isRefresh))
            }
        }
    }

    private fun updateState(state: LoadState) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "=== State Updated: $state")
        }
        currentState = state
        listener.onStateChanged(state)
    }
}
