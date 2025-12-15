package com.paging.core.source

import com.paging.core.model.PagingResult

/**
 * [Kotlin First] SAM 接口，支持 Lambda 写法
 */
fun interface PagingSource<Key : Any, Value : Any> {
    suspend fun load(key: Key?, pageSize: Int): PagingResult<Key, Value>
}