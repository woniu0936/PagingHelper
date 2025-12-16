package com.demo.pagehelper

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.data.LayoutType
import com.demo.pagehelper.ui.ArticleAdapter
import com.paging.core.model.LoadState
import com.demo.pagehelper.ui.AppPagingFooterAdapter
import com.paging.flow.setupPaging
import java.io.IOException

class GridLayoutDemoActivity : AppCompatActivity() {
    companion object {
        private const val GRID_SPAN_COUNT = 2   // 定义网格的列数
    }

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }
    private val articleAdapter = ArticleAdapter(LayoutType.GRID)
    private val footerAdapter = AppPagingFooterAdapter { viewModel.retry() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycler_view)
        fullScreen(findViewById(R.id.main))
        setupPaging()
    }

    private fun setupPaging() {
        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
        val fullScreenError: View = findViewById(R.id.full_screen_error_view)
        val emptyView: View = findViewById(R.id.empty_view)
        val errorTextView: TextView = findViewById(R.id.error_text_view)

        fullScreenError.setOnClickListener { viewModel.retry() }
        swipeRefresh.setOnRefreshListener { viewModel.refresh() }

        // LayoutManager needs to be set on the RecyclerView directly
        val gridLayoutManager = GridLayoutManager(this@GridLayoutDemoActivity, GRID_SPAN_COUNT)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                // Footer 占据所有列，否则占据一列
                return if (position == articleAdapter.itemCount && footerAdapter.itemCount > 0) {
                    GRID_SPAN_COUNT
                } else {
                    1
                }
            }
        }
        recyclerView.layoutManager = gridLayoutManager

        // 使用 paging-flow 提供的 DSL 来设置分页
        recyclerView.setupPaging(this, viewModel.paging) {
            // 绑定 Adapter
            this.adapter = articleAdapter
            this.footerAdapter = this@GridLayoutDemoActivity.footerAdapter


            // 监听加载状态
            onStateChanged { state ->
                // 控制下拉刷新圈的显示和隐藏
                swipeRefresh.isRefreshing = state is LoadState.Loading && state.isRefresh

                // 只有在刷新失败时才显示全屏错误
                val isListEmpty = articleAdapter.itemCount == 0
                fullScreenError.isVisible = isListEmpty && state is LoadState.Error && state.isRefresh
                if (fullScreenError.isVisible && state is LoadState.Error) {
                    val error = state.error
                    errorTextView.text = when (error) {
                        is IOException -> "网络连接失败，请检查设置"
                        else -> "发生未知错误: ${error.message}"
                    }
                }

                // 只有在加载完成且列表为空时显示空页面
                emptyView.isVisible = isListEmpty && state is LoadState.End
            }
        }
    }
}
