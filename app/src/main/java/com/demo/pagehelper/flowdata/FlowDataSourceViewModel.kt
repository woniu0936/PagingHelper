package com.demo.pagehelper.flowdata

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.pagehelper.data.ApiService
import com.demo.pagehelper.data.ListItem
import com.demo.pagehelper.data.MockApiService
import com.paging.core.model.PagingConfig
import com.paging.core.model.PagingResult
import com.paging.core.source.PagingSource
import com.paging.flow.FlowPaging
import kotlinx.coroutines.flow.*

class FlowDataSourceViewModel : ViewModel() {

    private val apiService: ApiService = MockApiService()
    private val pageSize = 20
    val pagingConfig = PagingConfig(pageSize = pageSize, prefetchDistance = pageSize)

    private val articlePagingSource = PagingSource<Int, ListItem> { key, pageSize ->
        Log.d("DebugSource", ">>> API Call Start. Key: $key")
        try {
            val currentPage = key ?: 0
            val pageData = apiService.getPageData(currentPage, pageSize).first()
            Log.d("DebugSource", "    API Raw Data Size: ${pageData.size}")
            val result =PagingResult(
                data = pageData,
                nextKey = if (pageData.size < pageSize) null else currentPage + 1
            )
            Log.d("DebugSource", "<<< API Call Success")
            return@PagingSource result
        }catch (e: Exception) {
            Log.e("DebugSource", "!!! API Call Failed inside Lambda !!!", e)
            throw e
        }

    }

    // Expose the FlowPaging object itself
    val paging = FlowPaging.create(
        scope = viewModelScope,
        source = articlePagingSource,
        config = pagingConfig
    )

    private val _selectedIds = MutableStateFlow<Set<Int>>(emptySet())
    val selectedIds: StateFlow<Set<Int>> = _selectedIds.asStateFlow()

    val items: StateFlow<List<SelectableItem>> = combine(
        paging.data, // Use exposed paging.data
        _selectedIds
    ) { data, ids ->
        data.map { listItem ->
            val isSelected = when (listItem) {
                is ListItem.ArticleItem -> listItem.article.id in ids
                is ListItem.Placeholder -> false
            }
            SelectableItem(data = listItem, isSelected = isSelected)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val loadState: StateFlow<com.paging.core.model.LoadState> = paging.loadState

    fun refresh() = paging.refresh()
    fun retry() = paging.retry()

    fun toggleSelection(articleId: Int) {
        _selectedIds.update { currentIds ->
            if (articleId in currentIds) {
                currentIds - articleId
            } else {
                currentIds + articleId
            }
        }
    }

    fun clearSelections() {
        _selectedIds.value = emptySet()
    }
}
