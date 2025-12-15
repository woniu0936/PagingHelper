package com.demo.pagehelper

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.pagehelper.datasource.DataSource
import com.demo.pagehelper.model.Article
import com.demo.pagehelper.model.ListItem
import com.demo.pagehelper.model.LoadState
import com.demo.pagehelper.model.PageResult
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.io.IOException
import kotlin.random.Random

class ArticleViewModel : ViewModel() {
    private val articleDataSource = object : DataSource<Int, ListItem> {
        override fun getInitialKey(): Int? = null
        override suspend fun loadPage(key: Int?): PageResult<Int, ListItem> {
            val currentPage = key ?: 0
            delay(1500)
            if (currentPage == 1 && Random.nextBoolean()) throw IOException("Mock network error!")
            val isEnd = currentPage >= 4
            val articles = if (isEnd) emptyList() else List(20) {
                ListItem.ArticleItem(Article(currentPage * 20 + it, "Article Title"))
            }
            return PageResult(data = articles, nextKey = if (isEnd) null else currentPage + 1)
        }
    }

    val helper = PagingHelper(viewModelScope, articleDataSource, placeholderGenerator = { List(20) { ListItem.Placeholder } })

    val items: StateFlow<List<ListItem>> = helper.items
    val loadState: StateFlow<LoadState> = helper.loadState

    fun refresh() = helper.refresh()
    fun loadMore() = helper.loadMore()
    fun retry() = helper.retry()
}
