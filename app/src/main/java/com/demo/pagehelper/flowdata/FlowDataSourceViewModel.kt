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

    // 4. 向 UI 层暴露数据和状态
    val items: Flow<List<ListItem>> = helper.items
    val loadState: Flow<LoadState> = helper.loadState

    fun refresh() = helper.refresh()
    fun loadMore() = helper.loadMore()
    fun retry() = helper.retry()

}