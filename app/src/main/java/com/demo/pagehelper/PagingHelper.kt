package com.demo.pagehelper

import com.demo.pagehelper.datasource.DataSource
import com.demo.pagehelper.model.ListItem
import com.demo.pagehelper.model.LoadState
import com.demo.pagehelper.model.toPagingError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private enum class LoadType { REFRESH, APPEND }

/**
 * 分页核心帮助类，管理所有分页逻辑、状态和数据。线程安全。
 *
 * @param scope CoroutineScope，通常是 ViewModelScope。
 * @param dataSource 业务层实现的数据源。
 * @param placeholderGenerator 一个可选的函数，用于在刷新时即时生成占位符列表以改善用户体验。
 */
class PagingHelper<Key : Any, T : Any>(
    private val scope: CoroutineScope,
    private val dataSource: DataSource<Key, T>,
    private val placeholderGenerator: (() -> List<T>)? = null
) {
    private val mutex = Mutex()

    private val _loadState = MutableStateFlow<LoadState>(LoadState.NotLoading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private var nextKey: Key? = dataSource.getInitialKey()
    private var lastFailedCall: Pair<LoadType, Key?>? = null

    /** 触发刷新操作。 */
    fun refresh() = scope.launch {
        placeholderGenerator?.let { _items.value = it() }
        performLoad(LoadType.REFRESH, dataSource.getInitialKey())
    }

    /** 触发加载更多操作。 */
    fun loadMore() = scope.launch { performLoad(LoadType.APPEND, nextKey) }

    /** 重试上一次失败的操作。 */
    fun retry() = scope.launch { lastFailedCall?.let { (type, key) -> performLoad(type, key) } }

    /**
     * [最终修正版] 执行实际的加载操作。
     * 内部通过 Mutex 保证原子性和线程安全，并使用分离的 LoadState 来精确地表达当前操作的状态。
     *
     * @param loadType 当前操作的类型 (REFRESH 或 APPEND)。
     * @param key 用于本次加载的键 (页码、cursor等)。
     */
    private suspend fun performLoad(loadType: LoadType, key: Key?) {
        // 防抖/节流：如果正在加载更多，则忽略新的加载更多请求。
        if (loadType == LoadType.APPEND && _loadState.value is LoadState.Append.Loading) {
            return
        }

        // 使用 Mutex 确保同一时间只有一个加载任务在执行，避免并发问题。
        mutex.withLock {
            // 在锁内双重检查，防止在等待锁的过程中状态已改变。
            if (loadType == LoadType.APPEND && (_loadState.value is LoadState.Append.Loading || _loadState.value is LoadState.End)) {
                return
            }

            // 如果是加载更多，但下一页的 key 已经是 null，说明已经到底了，直接更新状态并返回。
            if (loadType == LoadType.APPEND && key == null) {
                _loadState.value = LoadState.End
                return
            }

            // 根据操作类型，立即更新 LoadState，UI 会立刻响应。
            _loadState.value = if (loadType == LoadType.REFRESH) {
                LoadState.Refresh.Loading
            } else {
                LoadState.Append.Loading
            }

            runCatching {
                // 执行真正的、可能会失败的数据加载操作。
                dataSource.loadPage(key)
            }.onSuccess { result ->
                // 请求成功
                lastFailedCall = null // 清除失败记录
                nextKey = result.nextKey // 更新下一页的 key

                // 根据操作类型更新数据列表
                if (loadType == LoadType.REFRESH) {
                    _items.value = result.data // 刷新操作，完全替换列表
                } else {
                    // 加载更多操作，追加数据
                    // 检查当前列表是否是占位符，如果是，则先清空
                    val currentItems = if (_items.value.any { it is ListItem.Placeholder }) emptyList() else _items.value
                    _items.value = currentItems + result.data
                }

                // 根据 nextKey 判断是否已经加载完所有数据
                _loadState.value = if (result.nextKey == null) {
                    LoadState.End
                } else {
                    LoadState.NotLoading
                }

            }.onFailure { throwable ->
                // 请求失败
                lastFailedCall = loadType to key // 记录失败的上下文，以便重试

                // 将底层异常转换为业务错误
                val error = throwable.toPagingError()

                // 根据操作类型，更新为对应的 Error 状态
                _loadState.value = if (loadType == LoadType.REFRESH) {
                    LoadState.Refresh.Error(error)
                } else {
                    LoadState.Append.Error(error)
                }
            }
        }
    }
}
