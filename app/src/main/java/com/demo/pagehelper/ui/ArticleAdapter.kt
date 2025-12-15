package com.demo.pagehelper.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.pagehelper.R
import com.demo.pagehelper.model.LayoutType
import com.demo.pagehelper.model.ListItem
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * 纯粹的数据 Adapter，通过 [ListAdapter] 实现，支持渲染真实数据和占位符两种视图。
 */
open class ArticleAdapter(val layoutType: LayoutType) : ListAdapter<ListItem, RecyclerView.ViewHolder>(itemDiff) {
    companion object {
        private const val TYPE_ARTICLE = 0
        private const val TYPE_PLACEHOLDER = 1

        val itemDiff = object : DiffUtil.ItemCallback<ListItem>() {
            override fun areItemsTheSame(old: ListItem, new: ListItem): Boolean =
                (old is ListItem.ArticleItem && new is ListItem.ArticleItem && old.article.id == new.article.id) ||
                        (old is ListItem.Placeholder && new is ListItem.Placeholder)

            override fun areContentsTheSame(old: ListItem, new: ListItem): Boolean = old == new
        }
    }

    private val placeholderColors = intArrayOf(
        R.color.placeholder_bg_1, R.color.placeholder_bg_2, R.color.placeholder_bg_3,
        R.color.placeholder_bg_4, R.color.placeholder_bg_5, R.color.placeholder_bg_6,
        R.color.placeholder_bg_7, R.color.placeholder_bg_8
    )

    class ArticleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleView: TextView = view.findViewById(R.id.article_title)
        val imageView: ImageView = view.findViewById(R.id.article_image)
    }

    class PlaceholderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            // ShimmerFrameLayout 可能在 CardView 内部，需要查找
            (itemView.findViewWithTag<ShimmerFrameLayout>("shimmer_tag") ?: itemView as? ShimmerFrameLayout)?.startShimmer()
        }
    }

    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ListItem.ArticleItem -> TYPE_ARTICLE
        is ListItem.Placeholder -> TYPE_PLACEHOLDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ARTICLE) {
            val layoutId = when(layoutType) {
                LayoutType.LINEAR -> R.layout.list_item_article_linear
                LayoutType.GRID -> R.layout.list_item_article_grid
                LayoutType.STAGGERED -> R.layout.list_item_article_staggered
            }
            ArticleViewHolder(inflater.inflate(layoutId, parent, false))
        } else { // viewType is TYPE_PLACEHOLDER
            val layoutId = when(layoutType) {
                LayoutType.LINEAR -> R.layout.list_item_placeholder_linear_placeholder
                LayoutType.GRID -> R.layout.list_item_placeholder_grid_placeholder
                LayoutType.STAGGERED -> R.layout.list_item_placeholder_staggered_placeholder
            }
            PlaceholderViewHolder(inflater.inflate(layoutId, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is ArticleViewHolder -> {
                (getItem(position) as? ListItem.ArticleItem)?.let { item ->
                    holder.titleView.text = "ID: ${item.article.id} - ${item.article.title}"

                    // 设置随机背景色
                    val colorRes = placeholderColors[position % placeholderColors.size]
                    holder.imageView.setBackgroundResource(colorRes)

                    // 仅在瀑布流布局下设置随机高度
                    if (layoutType == LayoutType.STAGGERED) {
                        val lp = holder.itemView.layoutParams
                        // 使用 position hashcode 来得到一个稳定的随机高度，避免复用时高度变化
                        lp.height = (300 + (item.article.id.hashCode() % 300))
                        holder.itemView.layoutParams = lp
                    }
                }
            }
            is PlaceholderViewHolder -> {
                // 仅在瀑布流布局下，为骨架屏设置随机高度
                if (layoutType == LayoutType.STAGGERED) {
                    val lp = holder.itemView.layoutParams
                    // 使用 position 来得到一个伪随机的高度
                    lp.height = (300 + (position.hashCode() % 300))
                    holder.itemView.layoutParams = lp
                }
            }
        }
    }
}
