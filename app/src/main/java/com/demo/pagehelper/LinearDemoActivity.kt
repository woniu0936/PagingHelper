package com.demo.pagehelper

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.data.LayoutType
import com.demo.pagehelper.data.ListItem
import com.demo.pagehelper.ui.ArticleAdapter
import com.paging.core.model.LoadState
import com.demo.pagehelper.ui.AppPagingFooterAdapter
import com.paging.flow.setupPaging
import java.io.IOException

class LinearDemoActivity : AppCompatActivity() {

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }

    // ArticleAdapter 保持不变，它是一个标准的 ListAdapter
    private val articleAdapter = ArticleAdapter(LayoutType.LINEAR)

    // 使用 paging-core 库提供的 FooterAdapter
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
        recyclerView.layoutManager = LinearLayoutManager(this@LinearDemoActivity)

        // 使用 paging-flow 提供的 DSL 来设置分页
        recyclerView.setupPaging(this, viewModel.paging) {
            // 绑定 Adapter
            this.adapter = articleAdapter
            this.footerAdapter = this@LinearDemoActivity.footerAdapter


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
