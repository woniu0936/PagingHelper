package com.demo.pagehelper

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.demo.pagehelper.data.LayoutType
import com.demo.pagehelper.ui.ArticleAdapter
import com.paging.core.model.LoadState
import com.demo.pagehelper.ui.AppPagingFooterAdapter
import com.paging.flow.setupPaging
import java.io.IOException
import kotlin.random.Random

class StaggeredGridDemoActivity : AppCompatActivity() {
    companion object {
        private const val GRID_SPAN_COUNT = 2
    }

    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }
    private val articleAdapter = createStaggeredArticleAdapter()
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

        recyclerView.layoutManager = StaggeredGridLayoutManager(GRID_SPAN_COUNT, StaggeredGridLayoutManager.VERTICAL)

        recyclerView.setupPaging(this, viewModel.paging) {
            this.adapter = articleAdapter
            this.footerAdapter = this@StaggeredGridDemoActivity.footerAdapter


            onStateChanged { state ->
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

    private fun createStaggeredArticleAdapter(): ArticleAdapter {
        return object : ArticleAdapter(LayoutType.STAGGERED) {
            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                super.onBindViewHolder(holder, position)
                if (holder is ArticleAdapter.ArticleViewHolder) { // Use ArticleAdapter.ArticleViewHolder
                    val lp = holder.itemView.layoutParams
                    lp.height = (240 + Random.nextInt(800))
                    holder.itemView.layoutParams = lp
                }
            }
        }
    }
}
