package com.paging.core.model

/**
 * 分页数据载体
 */
data class PagingResult<Key, Value>(
    val data: List<Value>,
    val nextKey: Key?
)

// --- ★ Extension: List to Result ---
fun <T> List<T>.toPagingResult(currentKey: Int, pageSize: Int): PagingResult<Int, T> {
    val nextKey = if (this.size < pageSize || this.isEmpty()) null else currentKey + 1
    return PagingResult(this, nextKey)
}