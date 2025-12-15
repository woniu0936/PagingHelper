package com.paging.core.model

/**
 * 全局配置项
 */
data class PagingConfig @JvmOverloads constructor(
    @JvmField val pageSize: Int = 20,
    @JvmField val prefetchDistance: Int = 3
)