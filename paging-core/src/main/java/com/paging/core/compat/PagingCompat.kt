package com.paging.core.compat

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.paging.core.PagingHelper
import com.paging.core.dsl.PagingBuilder
import com.paging.core.source.AbstractPagingSource
import com.paging.core.ui.PagingFooterAdapter

/**
 * [Java Entry Point] 静态工厂风格
 */
object PagingCompat {
    @JvmStatic
    fun <Key : Any, Value : Any> with(recyclerView: RecyclerView): Builder<Key, Value> = Builder(recyclerView)

    class Builder<Key : Any, Value : Any>(private val recyclerView: RecyclerView) {
        private var lifecycleOwner: LifecycleOwner? = null
        private var adapter: RecyclerView.Adapter<*>? = null
        private var source: AbstractPagingSource<Key, Value>? = null
        private var footer: PagingFooterAdapter<*>? = null

        fun lifecycle(owner: LifecycleOwner) = apply { this.lifecycleOwner = owner }
        fun adapter(adapter: RecyclerView.Adapter<*>) = apply { this.adapter = adapter }
        fun source(source: AbstractPagingSource<Key, Value>) = apply { this.source = source }
        fun footer(footer: PagingFooterAdapter<*>) = apply { this.footer = footer }

        fun build(): PagingHelper<Key, Value> {
            val owner = lifecycleOwner ?: throw IllegalStateException("LifecycleOwner required")
            val ktBuilder = PagingBuilder<Key, Value>(recyclerView, owner.lifecycleScope)

            // 使用 Java 专用的设置方法
            if (adapter != null) ktBuilder.adapter = adapter!!
            if (footer != null) ktBuilder.footerAdapter = footer!!
            if (source != null) ktBuilder.setSourceCompat(source!!)

            return ktBuilder.build()
        }
    }
}