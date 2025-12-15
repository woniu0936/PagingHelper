package com.demo.pagehelper.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.demo.pagehelper.PagingHelper
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation

/**
 * 一个自定义的扩展函数，用于模仿 Paging3 库的便捷 API。
 * 它可以将一个数据 Adapter 和一个 PagingLoadStateAdapter 优雅地合并成一个 ConcatAdapter。
 *
 * @receiver 数据 Adapter (例如 ArticleAdapter)。
 * @param footer 用于展示加载状态的 PagingLoadStateAdapter。
 * @return 一个配置好的、包含数据和页脚的 ConcatAdapter。
 */
fun <T : RecyclerView.Adapter<out RecyclerView.ViewHolder>> T.withLoadStateFooter(
    footer: PagingLoadStateAdapter
): ConcatAdapter {
    // 创建 ConcatAdapter 的配置
    val config = ConcatAdapter.Config.Builder()
        .setIsolateViewTypes(true) // 这是关键！确保子 Adapter 的 ViewType 不会相互冲突。
        .build()

    // 返回一个包含原始数据 Adapter 和 Footer Adapter 的新 ConcatAdapter
    return ConcatAdapter(config, this, footer)
}

/**
 * [底层工具] 为 RecyclerView 注册一个生命周期安全的滚动监听器。
 * 当用户向下滑动到接近列表底部时，会触发 [onLoadMore] 回调。
 *
 * 这个函数是通用的，并且正确地处理了 [LinearLayoutManager], [GridLayoutManager]
 * 和 [StaggeredGridLayoutManager]。
 *
 * @param lifecycleOwner 用于将监听器的生命周期与给定的宿主（如 Fragment, Activity）绑定。
 * @param preloadOffset 预加载阈值。当最后一个可见项的位置 >= (总数 - 阈值) 时，触发加载。
 * @param onLoadMore 当满足预加载条件时要执行的回调函数。
 */
fun RecyclerView.addLoadMoreListener(
    lifecycleOwner: LifecycleOwner,
    preloadOffset: Int = 5,
    onLoadMore: () -> Unit
) {
    val listener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dy <= 0) return // 只在向下滑动时检查

            val layoutManager = recyclerView.layoutManager ?: return

            // 根据不同的 LayoutManager 类型，获取最后一个完全可见的 item 的位置
            val lastVisibleItemPosition = when (layoutManager) {
                // 注意：GridLayoutManager 是 LinearLayoutManager 的子类，
                // 所以这个分支会同时正确处理这两种类型的 LayoutManager。
                // 它们都使用 findLastVisibleItemPosition() 方法。
                is LinearLayoutManager -> layoutManager.findLastVisibleItemPosition()

                is StaggeredGridLayoutManager -> {
                    // StaggeredGridLayout 有多个列，可能导致 item 高度不同，
                    // 需要找到所有列中位置最大的那个，才是真正的“最后一个”。
                    val lastPositions = layoutManager.findLastVisibleItemPositions(null)
                    lastPositions.maxOrNull() ?: RecyclerView.NO_POSITION
                }

                // 如果是其他自定义的 LayoutManager，则不支持预加载
                else -> return
            }

            if (lastVisibleItemPosition == RecyclerView.NO_POSITION) {
                return
            }

            val totalItemCount = layoutManager.itemCount
            if (totalItemCount == 0) return

            // 判断是否满足预加载条件
            if (lastVisibleItemPosition >= totalItemCount - preloadOffset) {
                onLoadMore()
            }
        }
    }

    // ... 生命周期安全的监听器绑定逻辑 (不变) ...
    lifecycleOwner.lifecycleScope.launch {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            addOnScrollListener(listener)
            try {
                awaitCancellation()
            } finally {
                removeOnScrollListener(listener)
            }
        }
    }
}

/**
 * [便捷API] 将 RecyclerView 的加载更多行为直接绑定到一个 PagingHelper。
 * 这是 addLoadMoreListener 的一个专用版本，提供了更高的便利性。
 *
 * @param lifecycleOwner 生命周期拥有者
 * @param helper 你的 PagingHelper 实例
 * @param preloadOffset 预加载阈值
 */
fun <Key : Any, T : Any> RecyclerView.bindLoadMore(
    lifecycleOwner: LifecycleOwner,
    helper: PagingHelper<Key, T>,
    preloadOffset: Int = 5
) {
    // 复用底层的、通用的工具函数，提供 PagingHelper 的 loadMore 方法作为回调
    addLoadMoreListener(lifecycleOwner, preloadOffset) {
        helper.loadMore()
    }
}

/**
 * [便捷API工厂] 创建一个为 ConcatAdapter 自动配置好 SpanSizeLookup 的 GridLayoutManager。
 * 这使得当使用 GridLayoutManager 时，调用者无需再手动编写 SpanSizeLookup 的模板代码，
 * 实现了与 StaggeredGridLayoutManager 类似的使用体验。
 *
 * @param spanCount 网格的列数。
 * @return 一个配置好的、能自动让 PagingLoadStateAdapter 的 Footer 跨列的 GridLayoutManager。
 */
fun RecyclerView.autoConfiguredGridLayoutManager(spanCount: Int): GridLayoutManager {
    val layoutManager = GridLayoutManager(context, spanCount)
    layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
        override fun getSpanSize(position: Int): Int {
            val concatAdapter = this@autoConfiguredGridLayoutManager.adapter as? ConcatAdapter
            if (concatAdapter == null) {
                // 如果 adapter 不存在或不是 ConcatAdapter，则所有项都按默认占一列。
                return 1
            }
            // 采用基于 position 的判断逻辑，这是在 isolateViewTypes=true 下的正确做法。
            // 1. 检查当前 position 是否是 ConcatAdapter 中的最后一项。
            // 2. 检查 loadStateAdapter 是否真的有内容要显示 (itemCount > 0)。
            // 只有同时满足这两个条件，才说明这个位置是需要占据整行的 Footer。

            // 获取 loadStateAdapter，它被假定为 ConcatAdapter 中的最后一个 adapter
            val loadStateAdapter = concatAdapter.adapters.lastOrNull { it is PagingLoadStateAdapter } as? PagingLoadStateAdapter

            return if (loadStateAdapter != null && position == concatAdapter.itemCount - 1 && loadStateAdapter.itemCount > 0) {
                spanCount // 如果是 Footer，占据所有列
            } else {
                1 // 否则，占据一列
            }
        }
    }
    return layoutManager
}
