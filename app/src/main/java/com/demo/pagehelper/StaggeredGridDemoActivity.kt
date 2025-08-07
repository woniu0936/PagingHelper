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
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.launch
import kotlin.random.Random

// 假设所有的核心组件 (PagingHelper, Adapters, ViewModel等) 和 XML 布局都已存在

class StaggeredGridDemoActivity : AppCompatActivity() {
    companion object {
        private const val PRELOAD_OFFSET = 10
        private const val GRID_SPAN_COUNT = 2
    }

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }

    // 为了展示瀑布流效果，我们可以稍微改造一下 Adapter
    private val articleAdapter = createStaggeredArticleAdapter() // 使用一个工厂方法
    private val loadStateAdapter = PagingLoadStateAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        setupUI()
        setupObservers() // 与线性布局完全一样，此处省略
        if (savedInstanceState == null) viewModel.refresh()
    }

    private fun setupUI() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        // --- 关键改动点 ---

        // 1. 创建 StaggeredGridLayoutManager
        val staggeredGridLayoutManager = StaggeredGridLayoutManager(GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL)

        // 2. 将 LayoutManager 设置给 RecyclerView
        recyclerView.layoutManager = staggeredGridLayoutManager

        // 3. 使用我们的扩展函数组装 Adapter 和绑定加载更多逻辑
        val concatAdapter = articleAdapter.withLoadStateFooter(loadStateAdapter)
        recyclerView.adapter = concatAdapter

        recyclerView.bindLoadMore(
            lifecycleOwner = this,
            helper = viewModel.helper,
            preloadOffset = PRELOAD_OFFSET
        )

        // --- 其他设置保持不变 ---
        swipeRefresh.setOnRefreshListener {
            swipeRefresh.isRefreshing = true
            viewModel.refresh()
        }
    }

    /**
     * 创建一个特殊的 ArticleAdapter，它会给每个 Item 设置一个随机高度，以模拟瀑布流效果。
     */
    private fun createStaggeredArticleAdapter(): ArticleAdapter {
        return object : ArticleAdapter(ArticleAdapter.LayoutType.STAGGERED) {
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                if (holder is ArticleViewHolder) {
                    // 给 itemView 设置一个随机高度
                    val lp = holder.itemView.layoutParams
                    lp.height = (240 + Random.nextInt(800)) // 高度在 200dp 到 400dp 之间
                    holder.itemView.layoutParams = lp
                }
            }
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
                            errorTextView.text = when(state.error) {
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