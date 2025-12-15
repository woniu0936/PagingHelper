package com.paging.core.ui

import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * [顶级扩展] 自动适配网格和瀑布流的 Footer 跨列显示。
 *
 * 1. 修复了 android.util.Pair 的解构编译错误。
 * 2. 增加了对 StaggeredGridLayoutManager 的支持（统一设置 spanCount）。
 * 3. 针对 GridLayoutManager 自动注入 SpanSizeLookup。
 *
 * @param spanCount 网格或瀑布流的列数
 * @param originalSpanLookup (可选) 仅针对 GridLayoutManager，如果你原本的 Item 也有跨列逻辑，请在此传入
 */
fun RecyclerView.autoFitSpan(
    spanCount: Int,
    originalSpanLookup: GridLayoutManager.SpanSizeLookup? = null
) {
    val layoutManager = this.layoutManager ?: return

    when (layoutManager) {
        // === 场景 A: GridLayoutManager ===
        is GridLayoutManager -> {
            layoutManager.spanCount = spanCount
            layoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    val adapter = this@autoFitSpan.adapter ?: return 1

                    // 1. 如果是 ConcatAdapter，需要判断是否是 Footer
                    if (adapter is ConcatAdapter) {
                        // [修复点] android.util.Pair 不支持解构，直接使用 .first 获取 adapter
                        val pair = adapter.getWrappedAdapterAndPosition(position)
                        val childAdapter = pair.first

                        // 如果是我们的 FooterAdapter，强制跨满整行
                        // 注意：这里使用了泛型通配符 PagingFooterAdapter<*>
                        if (childAdapter is PagingFooterAdapter<*>) {
                            return spanCount
                        }
                    }

                    // 2. 否则使用用户原本的逻辑，默认为 1
                    return originalSpanLookup?.getSpanSize(position) ?: 1
                }
            }
        }

        // === 场景 B: StaggeredGridLayoutManager ===
        is StaggeredGridLayoutManager -> {
            // 瀑布流只需要设置 spanCount
            // Footer 的跨列逻辑由 PagingFooterAdapter.onBindViewHolder 中的 LayoutParams 处理
            // 这里只需确保列数设置正确即可
            layoutManager.spanCount = spanCount
        }

        // 其他布局管理器忽略
        else -> {
            // no-op
        }
    }
}