package com.demo.pagehelper.data

import com.demo.pagehelper.model.Article
import com.demo.pagehelper.model.ListItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.IOException
import kotlin.random.Random

/**
 * 模拟的网络服务接口。
 */
interface ApiService {
    /**
     * 定义了我们想要接入的分页接口，它返回一个 Flow。
     * @param pageIndex 请求的页码。
     * @param pageSize 每页的数据量。
     * @return 一个包含列表项数据流的 Flow。
     */
    fun getPageData(pageIndex: Int, pageSize: Int): Flow<List<ListItem>>
}

/**
 * 一个 ApiService 的模拟实现，用于演示和测试。
 */
class MockApiService : ApiService {
    /**
     * 使用 flow 构建器来模拟一个异步数据流。
     */
    override fun getPageData(pageIndex: Int, pageSize: Int): Flow<List<ListItem>> = flow {
        // 模拟网络延迟
        delay(1500)

        // 模拟随机的网络错误
        if (pageIndex > 0 && Random.nextInt(10) < 3) { // 大约30%的几率在加载更多时失败
            throw IOException("Mock network error on page $pageIndex")
        }

        // 模拟分页逻辑：假设总共有 5 页数据 (0, 1, 2, 3, 4)
        val isEnd = pageIndex >= 4

        val articles = if (isEnd) {
            // 如果是最后一页之后，返回空列表
            emptyList()
        } else {
            // 生成当前页的数据
            List(pageSize) { index ->
                val articleId = pageIndex * pageSize + index
                val article = Article(
                    id = articleId,
                    title = "Article #$articleId from Flow API"
                )
                ListItem.ArticleItem(article)
            }
        }

        // 通过 emit 发出数据，这将是 Flow.first() 捕获到的值
        emit(articles)
    }
}