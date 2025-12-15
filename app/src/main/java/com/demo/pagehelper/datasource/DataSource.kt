package com.demo.pagehelper.datasource

import com.demo.pagehelper.model.PageResult

/**
 * 数据源接口，定义了数据加载的契约。
 */
interface DataSource<Key, T> {
    /**
     * 加载分页数据。
     * @param key 当前页的请求键。首次加载时，`key` 可能为 null。
     * @return 包含数据和下一页键的 [PageResult]。
     */
    suspend fun loadPage(key: Key?): PageResult<Key, T>

    /**
     * 提供初始加载的键。
     * @return 初始加载键，如果从第一页开始则通常为 0 或 null。
     */
    fun getInitialKey(): Key?
}
