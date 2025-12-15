package com.paging.core.source

/**
 * [Java Compatible] 专为 Java 设计的基类
 */
abstract class AbstractPagingSource<Key : Any, Value : Any> {
    abstract fun load(key: Key?, pageSize: Int, callback: Callback<Key, Value>)

    interface Callback<Key, Value> {
        fun onResult(data: List<Value>, nextKey: Key?)
        fun onError(t: Throwable)
    }
}