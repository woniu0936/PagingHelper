package com.demo.pagehelper.data

import com.demo.pagehelper.DataSource
import com.demo.pagehelper.ListItem
import com.demo.pagehelper.PageResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * DataSource 适配器，它将返回 Flow 的 API 接口
 * 适配成 PagingHelper 所需的 suspend fun 接口。
 */
class FlowDataSourceAdapter(
    private val apiService: ApiService,
    private val pageSize: Int
) : DataSource<Int, ListItem> {

    /** 首次加载时使用第 0 页。 */
    override fun getInitialKey(): Int? = 0

    /**
     * 适配的核心逻辑：调用 Flow-based API 并使用 .first() 获取单个结果。
     */
    override suspend fun loadPage(key: Int?): PageResult<Int, ListItem> {
        val currentPageIndex = key ?: 0

        // 调用原始接口，得到一个 Flow
        val dataFlow: Flow<List<ListItem>> = apiService.getPageData(
            pageIndex = currentPageIndex,
            pageSize = pageSize
        )

        // 使用 .first() 挂起，直到 Flow 发出第一个值
        val pageData = dataFlow.first()

        // 根据返回的数据量判断是否是最后一页
        val isLastPage = pageData.size < pageSize

        // 计算下一页的 key
        val nextKey = if (isLastPage) null else currentPageIndex + 1

        // 包装成 PageResult
        return PageResult(data = pageData, nextKey = nextKey)
    }
}