package com.demo.pagehelper

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.pagehelper.data.Article
import com.demo.pagehelper.data.ListItem
import com.paging.core.model.PagingConfig
import com.paging.core.model.PagingResult
import com.paging.core.source.PagingSource
import com.paging.flow.FlowPaging
import kotlinx.coroutines.delay
import java.io.IOException
import kotlin.random.Random

class ArticleViewModel : ViewModel() {

    // 1. 定义 PagingSource，这是数据加载的逻辑核心
    private val articlePagingSource = PagingSource<Int, ListItem> { key, pageSize ->
        val currentPage = key ?: 0
        delay(1500) // 模拟网络延迟

        // 模拟网络错误
        if (currentPage == 1 && Random.nextBoolean()) {
            throw IOException("Mock network error!")
        }

        val isEnd = currentPage >= 4
        val data = if (isEnd) {
            emptyList()
        } else {
            List(pageSize) {
                val articleId = currentPage * pageSize + it
                ListItem.ArticleItem(Article(articleId, "Article Title #$articleId"))
            }
        }

        // 返回 PagingResult
        PagingResult(
            data = data,
            nextKey = if (isEnd) null else currentPage + 1
        )
    }

    // 2. 创建 FlowPaging 实例
    // 它是一个响应式容器，持有数据流 (data) 和状态流 (loadState)
    val paging = FlowPaging.create(
        scope = viewModelScope,
        source = articlePagingSource,
        config = PagingConfig(
            pageSize = 20,
            prefetchDistance = 5
        )
    )

    // 3. 向 UI 暴露控制器方法
    fun refresh() = paging.refresh()
    fun retry() = paging.retry()
    // loadMore() 通常由 RecyclerView 的滚动监听自动触发，无需手动暴露
}