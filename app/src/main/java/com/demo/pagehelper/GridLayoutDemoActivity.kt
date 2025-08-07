package com.demo.pagehelper

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
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch

// 假设所有的核心组件 (PagingHelper, Adapters, ViewModel等) 和 XML 布局都已存在

class GridLayoutDemoActivity : AppCompatActivity() {
    companion object {
        private const val PRELOAD_OFFSET = 10 // 网格布局一屏内容更多，可以适当增加预加载阈值
        private const val GRID_SPAN_COUNT = 2   // 定义网格的列数
    }

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }
    private val articleAdapter = ArticleAdapter(ArticleAdapter.LayoutType.GRID)
    private val loadStateAdapter = PagingLoadStateAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view) // 复用同一个布局
        setupUI()
        setupObservers() // 与线性布局完全一样，此处省略
        if (savedInstanceState == null) viewModel.refresh()
    }

    private fun setupUI() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        // --- 关键改动点 ---

        // 1. 创建 GridLayoutManager
        val gridLayoutManager = GridLayoutManager(this, GRID_SPAN_COUNT)

        // 2. 将 Adapter 组装起来
        val concatAdapter = articleAdapter.withLoadStateFooter(loadStateAdapter)

        // 3. (重要！) 将 Adapter 设置给 RecyclerView
        // 这一步必须在设置 SpanSizeLookup 之前，因为 lookup 逻辑需要访问 adapter。
        recyclerView.adapter = concatAdapter

        // 4. (关键修正) 设置 SpanSizeLookup，让 Footer 占据整行
        recyclerView.autoConfiguredGridLayoutManager(GRID_SPAN_COUNT)

        // 5. 将配置好的 LayoutManager 设置给 RecyclerView
        recyclerView.layoutManager = gridLayoutManager

        // 6. 绑定加载更多逻辑
        recyclerView.bindLoadMore(
            lifecycleOwner = this,
            helper = viewModel.helper,
            preloadOffset = PRELOAD_OFFSET
        )

        // 7. 其他设置
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = true
            viewModel.refresh()
        }
    }

    // setupObservers() 方法与线性布局完全一样，无需任何改动。
    private fun setupObservers() {
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        val fullScreenError: View = findViewById(R.id.full_screen_error_view)
        val emptyView: View = findViewById(R.id.empty_view)
        val errorTextView: TextView = findViewById(R.id.error_text_view)

        fullScreenError.setOnClickListener { viewModel.refresh() }

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
                            errorTextView.text = when (state.error) {
                                is PagingError.Network -> "网络连接失败，请检查设置"
                                is PagingError.Server -> "服务器开小差了 (Code: ${state.error.code})"
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