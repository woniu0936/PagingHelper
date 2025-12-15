package com.demo.pagehelper

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.model.LayoutType
import com.demo.pagehelper.model.ListItem
import com.demo.pagehelper.model.LoadState
import com.demo.pagehelper.model.PagingError
import com.demo.pagehelper.ui.ArticleAdapter
import com.demo.pagehelper.ui.PagingLoadStateAdapter
import com.demo.pagehelper.ui.bindLoadMore
import com.demo.pagehelper.ui.withLoadStateFooter
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch

class LinearDemoActivity : AppCompatActivity() {

    companion object {
        private const val PRELOAD_OFFSET = 5
    }

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }
    private val articleAdapter = ArticleAdapter(LayoutType.LINEAR)
    private val loadStateAdapter = PagingLoadStateAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recycler_view)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        setupUI()
        setupObservers()
        if (savedInstanceState == null) viewModel.refresh()
    }

    private fun setupUI() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)

        // ============================ [Final Polish] ============================
        // 使用我们自己创建的扩展函数，代码变得极其简洁和声明式！
        val concatAdapter = articleAdapter.withLoadStateFooter(footer = loadStateAdapter)
        // ======================================================================

        recyclerView.adapter = concatAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        // 调用我们最终的、最简洁的绑定函数！
        // 注意：这里需要能访问到 ViewModel 中的 PagingHelper 实例。
        // 如果 PagingHelper 是 private 的，可以暴露一个 public 的 helper 实例。
        recyclerView.bindLoadMore(
            lifecycleOwner = this,
            helper = viewModel.helper, // 假设 viewModel.helper 可访问
            preloadOffset = PRELOAD_OFFSET
        )


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
                        if (state !is LoadState.Refresh.Loading) swipeRefresh.isRefreshing = false

                        val isListEffectivelyEmpty = articleAdapter.currentList.isEmpty() || articleAdapter.currentList.all { it is ListItem.Placeholder }

                        fullScreenError.isVisible = isListEffectivelyEmpty && state is LoadState.Append.Error
                        if (state is LoadState.Append.Error) {
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