package com.demo.pagehelper.flowdata

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.demo.pagehelper.R
import com.demo.pagehelper.data.LayoutType
import com.demo.pagehelper.data.ListItem
import com.facebook.shimmer.ShimmerFrameLayout

/**
 * [增强] 为列表项增加选中状态的包装。
 * 包含原始数据和选中状态。
 *
 * @param data 原始的列表项数据 (ArticleItem 或 Placeholder)。
 * @param isSelected 当前项是否被选中。
 */
data class SelectableItem(
    val data: ListItem,
    val isSelected: Boolean = false
)

class FlowArticleAdapter(
    val layoutType: LayoutType,
    private val onItemToggled: (Int) -> Unit
) : ListAdapter<SelectableItem, RecyclerView.ViewHolder>(itemDiff) {

    companion object {
        private const val TYPE_ARTICLE = 10
        private const val TYPE_PLACEHOLDER = 11

        val itemDiff = object : DiffUtil.ItemCallback<SelectableItem>() {
            // isSelected 的变化也需要触发更新，所以比较整个对象
            override fun areItemsTheSame(old: SelectableItem, new: SelectableItem): Boolean {
                val oldData = old.data
                val newData = new.data
                // 使用 ID 来判断是否是同一个 item
                return (oldData is ListItem.ArticleItem && newData is ListItem.ArticleItem && oldData.article.id == newData.article.id) ||
                        (oldData is ListItem.Placeholder && newData is ListItem.Placeholder)
            }

            override fun areContentsTheSame(old: SelectableItem, new: SelectableItem): Boolean = old == new
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
        val checkBox: AppCompatCheckBox = view.findViewById(R.id.cb_item)
    }

    class PlaceholderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        init {
            // ShimmerFrameLayout 可能在 CardView 内部，需要查找
            (itemView.findViewWithTag<ShimmerFrameLayout>("shimmer_tag") ?: itemView as? ShimmerFrameLayout)?.startShimmer()
        }
    }

    override fun getItemViewType(position: Int): Int {
        // getItem(position) 返回的是 SelectableItem
        // 我们需要关心的是它内部的 data 是 ArticleItem 还是 Placeholder
        return when (getItem(position).data) {
            is ListItem.ArticleItem -> TYPE_ARTICLE
            is ListItem.Placeholder -> TYPE_PLACEHOLDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_ARTICLE) {
            val layoutId = when (layoutType) {
                LayoutType.LINEAR -> R.layout.list_item_article_linear_checkbox
                LayoutType.GRID -> R.layout.list_item_article_grid_checkbox
                LayoutType.STAGGERED -> R.layout.list_item_article_staggered_checkbox
            }
            ArticleViewHolder(inflater.inflate(layoutId, parent, false))
        } else { // viewType is TYPE_PLACEHOLDER
            val layoutId = when (layoutType) {
                LayoutType.LINEAR -> R.layout.list_item_placeholder_linear_placeholder
                LayoutType.GRID -> R.layout.list_item_placeholder_grid_placeholder
                LayoutType.STAGGERED -> R.layout.list_item_placeholder_staggered_placeholder
            }
            PlaceholderViewHolder(inflater.inflate(layoutId, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        // 获取当前位置的 SelectableItem
        val selectableItem = getItem(position)
        when (holder) {
            is ArticleViewHolder -> {
                (selectableItem.data as? ListItem.ArticleItem)?.let { item ->
                    holder.titleView.text = "ID: ${item.article.id} - ${item.article.title}"

                    // 设置随机背景色
                    val colorRes = placeholderColors[position % placeholderColors.size]
                    holder.imageView.setBackgroundResource(colorRes)
                    holder.checkBox.isChecked = selectableItem.isSelected

                    // 仅在瀑布流布局下设置随机高度
                    if (layoutType == LayoutType.STAGGERED) {
                        val lp = holder.itemView.layoutParams
                        // 使用 position hashcode 来得到一个稳定的随机高度，避免复用时高度变化
                        lp.height = (300 + (item.article.id.hashCode() % 300))
                        holder.itemView.layoutParams = lp
                    }
                    // --- 设置点击监听 ---
                    holder.itemView.setOnClickListener {
                        // 当 itemView 被点击时，调用回调，通知 ViewModel
                        onItemToggled(item.article.id)
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
