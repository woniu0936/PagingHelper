package com.paging.core.source

import com.paging.core.model.PagingResult

/**
 * [DSL Factory] 允许配置 initialKey 的工厂方法
 */
fun <Key : Any, Value : Any> PagingSource(
    initialKey: Key? = null,
    loader: suspend (key: Key?, pageSize: Int) -> PagingResult<Key, Value>
): PagingSource<Key, Value> = PagingSource { key, pageSize ->
    // 闭包捕获 initialKey，处理刷新时的默认值
    val actualKey = key ?: initialKey
    loader(actualKey, pageSize)
}

/**
 * [Operator] 数据转换操作符 (Decorator Pattern)
 */
fun <Key : Any, Input : Any, Output : Any> PagingSource<Key, Input>.map(
    transform: (Input) -> Output
): PagingSource<Key, Output> = PagingSource { key, pageSize ->
    val result = this@map.load(key, pageSize)
    PagingResult(result.data.map(transform), result.nextKey)
}