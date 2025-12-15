package com.paging.core.ui

import androidx.annotation.RestrictTo
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.model.LoadState
import com.paging.core.model.PagingConfig

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class PagingBinder(
    private val recyclerView: RecyclerView,
    private val config: PagingConfig,
    private val loadMoreAction: () -> Unit
) {
    fun attach(adapter: RecyclerView.Adapter<*>, footer: RecyclerView.Adapter<*>) {
        recyclerView.adapter = ConcatAdapter(
            ConcatAdapter.Config.Builder().setIsolateViewTypes(false).build(),
            adapter, footer
        )
        recyclerView.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: android.view.View) {
                val pos = recyclerView.getChildAdapterPosition(view)
                val count = recyclerView.adapter?.itemCount ?: 0
                if (pos >= count - 1 - config.prefetchDistance) loadMoreAction()
            }

            override fun onChildViewDetachedFromWindow(view: android.view.View) {}
        })
    }

    fun bindState(footer: PagingFooterAdapter<*>, state: LoadState) {
        footer.state = state
    }
}