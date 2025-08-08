package com.demo.pagehelper.flowdata

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.pagehelper.ListItem
import com.demo.pagehelper.LoadState
import com.demo.pagehelper.PagingHelper
import com.demo.pagehelper.data.ApiService
import com.demo.pagehelper.data.FlowDataSourceAdapter
import com.demo.pagehelper.data.MockApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class FlowDataSourceViewModel : ViewModel(){

    // 1. 创建模拟 ApiService 的实例
    private val apiService: ApiService = MockApiService()

    private val pageSize = 20

    // 2. 创建 DataSource 适配器
    private val articleDataSource = FlowDataSourceAdapter(apiService, pageSize)

    // 3. 将适配好的 DataSource 传入 PagingHelper
    val helper = PagingHelper(
        viewModelScope,
        articleDataSource,
        placeholderGenerator = { List(10) { ListItem.Placeholder } }
    )

    /**
     * [核心] 使用 MutableStateFlow<Set<Int>> 来存储所有被选中的 item 的 ID。
     * Set 提供了高效的增、删、查操作。
     */
    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    /**
     * [修正] 最终的、单一的 UI 状态流。
     * 使用 combine 将所有底层状态合并成一个原子性的、一致的 UI 状态。
     */
    val uiState: StateFlow<PagingUiState> = combine(
        helper.items,
        helper.loadState,
        _selectedIds
    ) { articleItems, loadState, selectedIds ->
        // 当底层任何一个 Flow 更新时，这里都会重新计算出最新的 UI 状态。

        // 只有在刷新成功后，才将原始数据映射为 SelectableItem。
        // 在刷新加载中时，PagingHelper 的 items Flow 会是占位符列表。
        val selectableItems = if (loadState is LoadState.Refresh.Loading) {
            articleItems.map { SelectableItem(it, false) } // 假设占位符也用 SelectableItem 包装
        } else {
            articleItems.map { listItem ->
                val isSelected = when (listItem) {
                    is ListItem.ArticleItem -> listItem.article.id in selectedIds
                    is ListItem.Placeholder -> false
                }
                SelectableItem(data = listItem, isSelected = isSelected)
            }
        }

        PagingUiState(
            items = selectableItems,
            loadState = loadState,
            selectedIds = selectedIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PagingUiState() // 初始状态
    )


    // 4. 向 UI 层暴露数据和状态
    val items: Flow<List<ListItem>> = helper.items
    val loadState: Flow<LoadState> = helper.loadState

    fun refresh() = helper.refresh()
    fun loadMore() = helper.loadMore()
    fun retry() = helper.retry()

    fun toggleSelection(articleId: Int) {
        _selectedIds.value = _selectedIds.value.let {
            if (articleId in it) it - articleId else it + articleId
        }
    }
    fun clearSelections() { _selectedIds.value = emptySet() }
}