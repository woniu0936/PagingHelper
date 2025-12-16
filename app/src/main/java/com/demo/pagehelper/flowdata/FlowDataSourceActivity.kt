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
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.R
import com.demo.pagehelper.data.LayoutType
import com.demo.pagehelper.fullScreen
import com.demo.pagehelper.ui.AppPagingFooterAdapter
import com.paging.core.model.LoadState
import com.paging.core.ui.PagingBinder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.IOException

class FlowDataSourceActivity : AppCompatActivity() {

    companion object {
        private const val GRID_SPAN_COUNT = 2
    }

    private val viewModel: FlowDataSourceViewModel by lazy { ViewModelProvider(this)[FlowDataSourceViewModel::class.java] }

    private val articleAdapter = FlowArticleAdapter(LayoutType.GRID) { articleId ->
        viewModel.toggleSelection(articleId)
    }

    private val footerAdapter = AppPagingFooterAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        fullScreen(findViewById(R.id.main))
        setupUI()
        setupObservers()
    }

    private fun setupUI() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        val fullScreenError: View = findViewById(R.id.full_screen_error_view)

        fullScreenError.setOnClickListener { viewModel.retry() }
        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        recyclerView.layoutManager = when (articleAdapter.layoutType) {
            LayoutType.LINEAR -> LinearLayoutManager(this)
            LayoutType.GRID -> {
                val gridLayoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)
                gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int {
                        // This logic should be correct, as PagingBinder uses ConcatAdapter
                        return if (position == articleAdapter.itemCount && footerAdapter.itemCount > 0) {
                            GRID_SPAN_COUNT
                        } else {
                            1
                        }
                    }
                }
                gridLayoutManager
            }
            LayoutType.STAGGERED -> StaggeredGridLayoutManager(GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL)
        }

        // Manual PagingBinder setup
        val binder = PagingBinder(recyclerView, viewModel.pagingConfig, viewModel.paging::loadMore)
        binder.attach(articleAdapter, footerAdapter)
    }

    private fun setupObservers() {
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        val fullScreenError: View = findViewById(R.id.full_screen_error_view)
        val emptyView: View = findViewById(R.id.empty_view)
        val errorTextView: TextView = findViewById(R.id.error_text_view)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.items.collectLatest { items ->
                        articleAdapter.submitList(items)
                    }
                }

                launch {
                    viewModel.loadState.collectLatest { state ->
                        footerAdapter.state = state
                        swipeRefresh.isRefreshing = state is LoadState.Loading && state.isRefresh

                        val isListEmpty = articleAdapter.itemCount == 0
                        fullScreenError.isVisible = isListEmpty && state is LoadState.Error && state.isRefresh
                        if (fullScreenError.isVisible && state is LoadState.Error) {
                            val error = state.error
                            errorTextView.text = when (error) {
                                is IOException -> "网络连接失败，请检查设置"
                                else -> "发生未知错误: ${error.message}"
                            }
                        }
                        emptyView.isVisible = isListEmpty && state is LoadState.End
                    }
                }
            }
        }
    }
}
