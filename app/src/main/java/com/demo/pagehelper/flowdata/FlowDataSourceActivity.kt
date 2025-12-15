package com.demo.pagehelper.flowdata

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.model.LayoutType
import com.demo.pagehelper.model.ListItem
import com.demo.pagehelper.model.LoadState
import com.demo.pagehelper.R
import com.demo.pagehelper.ui.PagingLoadStateAdapter
import com.demo.pagehelper.ui.autoConfiguredGridLayoutManager
import com.demo.pagehelper.ui.bindLoadMore
import com.demo.pagehelper.ui.withLoadStateFooter
import kotlinx.coroutines.launch

class FlowDataSourceActivity : AppCompatActivity() {

    companion object {
        private const val PRELOAD_OFFSET = 10
        private const val GRID_SPAN_COUNT = 2
    }

    private val viewModel: FlowDataSourceViewModel by lazy { ViewModelProvider(this)[FlowDataSourceViewModel::class.java] }

    // 切换这个枚举值即可改变整个列表的布局！// 第一个参数.GRID或 .LINEAR, .STAGGERED
    private val articleAdapter = FlowArticleAdapter(LayoutType.GRID) { articleId ->
        viewModel.toggleSelection(articleId)
    }

    private val loadStateAdapter = PagingLoadStateAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        setupUI()
        setupObservers()
        if (savedInstanceState == null) viewModel.refresh()
    }

    private fun setupUI() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        val concatAdapter = articleAdapter.withLoadStateFooter(loadStateAdapter)
        recyclerView.adapter = concatAdapter

        // 根据 Adapter 的类型选择合适的 LayoutManager
        recyclerView.layoutManager = when (articleAdapter.layoutType) {
            LayoutType.LINEAR -> LinearLayoutManager(this)
            LayoutType.GRID -> recyclerView.autoConfiguredGridLayoutManager(GRID_SPAN_COUNT)
            LayoutType.STAGGERED -> StaggeredGridLayoutManager(GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL)
        }

        recyclerView.bindLoadMore(this, viewModel.helper, PRELOAD_OFFSET)

        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = true
            viewModel.refresh()
        }
    }

    private fun setupObservers() {
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        val fullScreenError: View = findViewById(R.id.full_screen_error_view)
        val emptyView: View = findViewById(R.id.empty_view)
        val errorTextView: TextView = findViewById(R.id.error_text_view)
        val errorRetryButton: View = findViewById(R.id.error_retry_button)

        errorRetryButton.setOnClickListener { viewModel.refresh() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // [修正] 只观察这一个 uiState Flow！
                viewModel.uiState.collect { state ->
                    // 1. 更新列表数据
                    articleAdapter.submitList(state.items)

                    // 2. 更新 Footer 状态，这里的逻辑现在变得非常清晰和健壮！
                    val hasRealData = state.items.any { it.data is ListItem.ArticleItem }
                    loadStateAdapter.isDataEmpty = !hasRealData
                    // Footer 只对 Append 和 End 状态做出反应
                    loadStateAdapter.loadState = if (state.loadState is LoadState.Append || state.loadState is LoadState.End) {
                        state.loadState
                    } else {
                        LoadState.NotLoading // 在刷新等其他状态下，强制隐藏 Footer
                    }

                    // 3. 更新下拉刷新圈，它只对 Refresh.Loading 状态做出反应
                    swipeRefresh.isRefreshing = state.loadState is LoadState.Refresh.Loading

                    // 4. 更新全屏错误状态，它只对 Refresh.Error 状态做出反应
                    val isListEmpty = state.items.isEmpty()
                    fullScreenError.isVisible = isListEmpty && state.loadState is LoadState.Refresh.Error
                    if (state.loadState is LoadState.Refresh.Error) {
//                        errorTextView.text = when(val error = state.loadState.error) {
//                            // ... 更新错误文本 ...
//                        }
                    }

                    // 5. 更新空页面状态
                    emptyView.isVisible = isListEmpty && state.loadState is LoadState.End
                }
            }
        }
    }
}
