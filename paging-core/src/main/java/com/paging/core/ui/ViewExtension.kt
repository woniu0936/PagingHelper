package com.paging.core.ui

import android.view.View
import androidx.core.view.isVisible
import com.paging.core.model.LoadState
import com.paging.core.model.isLoading

/**
 * [智能可见性绑定]
 * 当状态为 Loading 时显示 View，否则隐藏。
 *
 * 示例：progressBar.visibleOnLoading(state)
 */
fun View.visibleOnLoading(state: LoadState) {
    this.isVisible = state.isLoading
}

/**
 * [智能可见性绑定]
 * 当状态为 Error 时显示 View，否则隐藏。
 */
fun View.visibleOnError(state: LoadState) {
    this.isVisible = state is LoadState.Error
}

/**
 * [智能可见性绑定]
 * 当状态为 End (没有更多) 时显示 View。
 */
fun View.visibleOnEnd(state: LoadState) {
    this.isVisible = state is LoadState.End
}

/**
 * [一键绑定点击重试]
 * 仅在 Error 状态下响应点击。
 */
fun View.clickToRetry(state: LoadState, action: () -> Unit) {
    this.setOnClickListener {
        if (state is LoadState.Error) {
            action()
        }
    }
}