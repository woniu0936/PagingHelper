package com.demo.pagehelper.data

/**
 * 使用 Sealed Interface 统一表示列表中的真实数据和占位符。
 */
sealed interface ListItem {
    data class ArticleItem(val article: Article) : ListItem
    object Placeholder : ListItem
}
