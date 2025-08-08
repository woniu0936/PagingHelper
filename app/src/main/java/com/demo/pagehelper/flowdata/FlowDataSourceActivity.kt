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
import com.demo.pagehelper.ArticleAdapter
import com.demo.pagehelper.ListItem
import com.demo.pagehelper.LoadState
import com.demo.pagehelper.PagingError
import com.demo.pagehelper.PagingLoadStateAdapter
import com.demo.pagehelper.R
import com.demo.pagehelper.autoConfiguredGridLayoutManager
import com.demo.pagehelper.bindLoadMore
import com.demo.pagehelper.withLoadStateFooter
import kotlinx.coroutines.launch

class FlowDataSourceActivity : AppCompatActivity() {

    companion object {
        private const val PRELOAD_OFFSET = 10
        private const val GRID_SPAN_COUNT = 2
    }

    private val viewModel: FlowDataSourceViewModel by lazy { ViewModelProvider(this)[FlowDataSourceViewModel::class.java] }

    // 切换这个枚举值即可改变整个列表的布局！
    private val articleAdapter = ArticleAdapter(ArticleAdapter.LayoutType.GRID) // 或 .LINEAR, .STAGGERED

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
        recyclerView.layoutManager = when(articleAdapter.layoutType) {
            ArticleAdapter.LayoutType.LINEAR -> LinearLayoutManager(this)
            ArticleAdapter.LayoutType.GRID -> recyclerView.autoConfiguredGridLayoutManager(GRID_SPAN_COUNT)
            ArticleAdapter.LayoutType.STAGGERED -> StaggeredGridLayoutManager(GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL)
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
                launch {
                    viewModel.items.collect { list ->
                        articleAdapter.submitList(list)
                        loadStateAdapter.isDataEmpty = list.isEmpty()
                    }
                }
                launch {
                    viewModel.loadState.collect { state ->
                        loadStateAdapter.loadState = state
                        if (state !is LoadState.Loading) swipeRefresh.isRefreshing = false

                        val isListEffectivelyEmpty = articleAdapter.currentList.isEmpty() || articleAdapter.currentList.all { it is ListItem.Placeholder }

                        fullScreenError.isVisible = isListEffectivelyEmpty && state is LoadState.Error
                        if (state is LoadState.Error) {
                            errorTextView.text = when(val error = state.error) {
                                is PagingError.Network -> "网络连接失败，请检查设置"
                                is PagingError.Server -> "服务器开小差了 (Code: ${error.code})"
                                is PagingError.Unknown -> "发生未知错误"
                            }
                        }
                        emptyView.isVisible = isListEffectivelyEmpty && state is LoadState.End
                    }
                }
            }
        }
    }

}