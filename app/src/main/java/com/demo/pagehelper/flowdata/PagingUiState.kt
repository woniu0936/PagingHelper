package com.demo.pagehelper.flowdata

import com.demo.pagehelper.ListItem
import com.demo.pagehelper.LoadState

/**
 * [终极方案] 一个统一的 UI 状态数据类。
 * 它封装了 UI 渲染所需的所有信息：列表数据、加载状态、选中状态等。
 * 这避免了在 Activity 中观察多个 Flow 并处理它们之间时序问题的复杂性。
 */
data class PagingUiState(
    val items: List<SelectableItem> = emptyList(),
    val loadState: LoadState = LoadState.NotLoading,
    val selectedIds: Set<Int> = emptySet()
    // 未来还可以加入其他UI状态，比如 "是否处于选择模式" 等
)

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