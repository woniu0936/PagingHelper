package com.demo.pagehelper

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.facebook.shimmer.ShimmerFrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.random.Random

// region ==================== 1. 核心接口 & 状态模型 ====================

/**
 * 分页加载结果的数据类。
 *
 * @param Key 类型参数，表示下一页的键。
 * @param T 类型参数，表示数据的类型。
 * @property data 当前页的数据列表。
 * @property nextKey 下一页的请求键，如果为 null，表示没有更多数据。
 */
data class PageResult<Key, T>(
    val data: List<T>,
    val nextKey: Key?
)

/**
 * 数据源接口，定义了数据加载的契约。
 */
interface DataSource<Key, T> {
    /**
     * 加载分页数据。
     * @param key 当前页的请求键。首次加载时，`key` 可能为 null。
     * @return 包含数据和下一页键的 [PageResult]。
     */
    suspend fun loadPage(key: Key?): PageResult<Key, T>

    /**
     * 提供初始加载的键。
     * @return 初始加载键，如果从第一页开始则通常为 0 或 null。
     */
    fun getInitialKey(): Key?
}

/**
 * 结构化的业务错误实体，用于精确的错误处理和监控。
 */
sealed class PagingError {
    data class Network(val cause: Throwable) : PagingError()
    data class Server(val code: Int, val message: String) : PagingError()
    data class Unknown(val cause: Throwable) : PagingError()
}

/**
 * 将底层异常映射为定义的业务错误的扩展函数。
 */
fun Throwable.toPagingError(): PagingError {
    return when (this) {
        is IOException -> PagingError.Network(this)
        // 在实际项目中，这里可以判断 Retrofit 的 HttpException 等更具体的网络错误类型
        // is HttpException -> PagingError.Server(this.code(), this.message())
        else -> PagingError.Unknown(this)
    }
}

/**
 * 列表加载状态机，持有 [PagingError] 以提供丰富的错误信息。
 */
sealed class LoadState {
    object Loading : LoadState()
    data class Error(val error: PagingError) : LoadState()
    object End : LoadState()
    object NotLoading : LoadState()
}

// endregion

// region ==================== 2. 状态管理器 PagingHelper ====================

private enum class LoadType { REFRESH, APPEND }

/**
 * 分页核心帮助类，管理所有分页逻辑、状态和数据。线程安全。
 *
 * @param scope CoroutineScope，通常是 ViewModelScope。
 * @param dataSource 业务层实现的数据源。
 * @param placeholderGenerator 一个可选的函数，用于在刷新时即时生成占位符列表以改善用户体验。
 */
class PagingHelper<Key : Any, T : Any>(
    private val scope: CoroutineScope,
    private val dataSource: DataSource<Key, T>,
    private val placeholderGenerator: (() -> List<T>)? = null
) {
    private val mutex = Mutex()

    private val _loadState = MutableStateFlow<LoadState>(LoadState.NotLoading)
    val loadState: StateFlow<LoadState> = _loadState.asStateFlow()

    private val _items = MutableStateFlow<List<T>>(emptyList())
    val items: StateFlow<List<T>> = _items.asStateFlow()

    private var nextKey: Key? = dataSource.getInitialKey()
    private var lastFailedCall: Pair<LoadType, Key?>? = null

    /** 触发刷新操作。 */
    fun refresh() = scope.launch {
        placeholderGenerator?.let { _items.value = it() }
        performLoad(LoadType.REFRESH, dataSource.getInitialKey())
    }

    /** 触发加载更多操作。 */
    fun loadMore() = scope.launch { performLoad(LoadType.APPEND, nextKey) }

    /** 重试上一次失败的操作。 */
    fun retry() = scope.launch { lastFailedCall?.let { (type, key) -> performLoad(type, key) } }

    private suspend fun performLoad(loadType: LoadType, key: Key?) {
        // 通过检查当前状态实现防抖，防止在加载中时重复触发加载更多。
        if (loadType == LoadType.APPEND && _loadState.value is LoadState.Loading) return

        mutex.withLock {
            if (loadType == LoadType.APPEND && (_loadState.value is LoadState.Loading || _loadState.value is LoadState.End)) return

            _loadState.value = LoadState.Loading
            runCatching {
                dataSource.loadPage(key)
            }.onSuccess { result ->
                lastFailedCall = null
                nextKey = result.nextKey
                val currentItems = if (loadType == LoadType.REFRESH || _items.value.any { it is ListItem.Placeholder }) emptyList() else _items.value
                _items.value = currentItems + result.data
                _loadState.value = if (result.nextKey == null) LoadState.End else LoadState.NotLoading
            }.onFailure { throwable ->
                lastFailedCall = loadType to key
                _loadState.value = LoadState.Error(throwable.toPagingError())
            }
        }
    }
}

// endregion

// region ==================== 3. UI组件 (Adapter) ====================

/**
 * 使用 Sealed Interface 统一表示列表中的真实数据和占位符。
 */
sealed interface ListItem {
    data class ArticleItem(val article: Article) : ListItem
    object Placeholder : ListItem
}

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

    enum class LayoutType {
        LINEAR, GRID, STAGGERED
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
        if (holder is ArticleViewHolder) {

        }
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

/**
 * Footer Adapter，负责展示 Loading / Error / End 状态，设计与 [ConcatAdapter] 配合使用。
 */
class PagingLoadStateAdapter(private val retry: () -> Unit) : RecyclerView.Adapter<PagingLoadStateAdapter.LoadStateViewHolder>() {

    var loadState: LoadState = LoadState.NotLoading
        set(value) {
            if (field != value) {
                val oldItemVisible = displayAsItem(field)
                val newItemVisible = displayAsItem(value)
                field = value
                if (oldItemVisible != newItemVisible) {
                    if (newItemVisible) notifyItemInserted(0) else notifyItemRemoved(0)
                } else if (newItemVisible) {
                    notifyItemChanged(0)
                }
            }
        }

    var isDataEmpty: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                if (displayAsItem(loadState)) {
                    if (value) notifyItemRemoved(0) else notifyItemInserted(0)
                }
            }
        }

    private fun displayAsItem(state: LoadState) = state is LoadState.Loading || state is LoadState.Error || state is LoadState.End
    override fun getItemCount(): Int = if (!isDataEmpty && displayAsItem(loadState)) 1 else 0
    override fun onCreateViewHolder(parent: ViewGroup, vt: Int) =
        LoadStateViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.list_item_footer, parent, false), retry)

    override fun onBindViewHolder(holder: LoadStateViewHolder, pos: Int) = holder.bind(loadState)

    class LoadStateViewHolder(itemView: View, retry: () -> Unit) : RecyclerView.ViewHolder(itemView) {
        private val progress: ProgressBar = itemView.findViewById(R.id.footer_progress)
        private val retryButton: Button = itemView.findViewById(R.id.footer_retry_button)
        private val endText: TextView = itemView.findViewById(R.id.footer_end_text)

        init {
            retryButton.setOnClickListener { retry() }
        }

        fun bind(state: LoadState) {
            (itemView.layoutParams as? StaggeredGridLayoutManager.LayoutParams)?.isFullSpan = true
            progress.isVisible = state is LoadState.Loading
            retryButton.isVisible = state is LoadState.Error
            endText.isVisible = state is LoadState.End
        }
    }
}

// endregion

// region ==================== 4. 业务逻辑层 (ViewModel & Model) ====================

data class Article(val id: Int, val title: String)

class ArticleViewModel : ViewModel() {
    private val articleDataSource = object : DataSource<Int, ListItem> {
        override fun getInitialKey(): Int? = null
        override suspend fun loadPage(key: Int?): PageResult<Int, ListItem> {
            val currentPage = key ?: 0
            delay(1500)
            if (currentPage == 1 && Random.nextBoolean()) throw IOException("Mock network error!")
            val isEnd = currentPage >= 4
            val articles = if (isEnd) emptyList() else List(20) {
                ListItem.ArticleItem(Article(currentPage * 20 + it, "Article Title"))
            }
            return PageResult(data = articles, nextKey = if (isEnd) null else currentPage + 1)
        }
    }

    val helper = PagingHelper(viewModelScope, articleDataSource, placeholderGenerator = { List(20) { ListItem.Placeholder } })

    val items: StateFlow<List<ListItem>> = helper.items
    val loadState: StateFlow<LoadState> = helper.loadState

    fun refresh() = helper.refresh()
    fun loadMore() = helper.loadMore()
    fun retry() = helper.retry()
}

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

// endregion

// region ==================== 5. UI展现层 (Activity) ====================
//class MainActivity : AppCompatActivity() {
//    companion object {
//        private const val PRELOAD_OFFSET = 5
//    }
//
//    private val viewModel: ArticleViewModel by lazy { ViewModelProvider(this)[ArticleViewModel::class.java] }
//    private val articleAdapter = ArticleAdapter()
//    private val loadStateAdapter = PagingLoadStateAdapter { viewModel.retry() }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        setupUI()
//        setupObservers()
//        if (savedInstanceState == null) viewModel.refresh()
//    }
//
//    private fun setupUI() {
//        val recyclerView: RecyclerView = findViewById(R.id.recycler_view)
//        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
//
//        // ============================ [Final Polish] ============================
//        // 使用我们自己创建的扩展函数，代码变得极其简洁和声明式！
//        val concatAdapter = articleAdapter.withLoadStateFooter(footer = loadStateAdapter)
//        // ======================================================================
//
//        recyclerView.adapter = concatAdapter
//        recyclerView.layoutManager = LinearLayoutManager(this)
//
//        recyclerView.bindLoadMore(
//        lifecycleOwner = this,
//        helper = viewModel.helper, // 假设 viewModel.helper 可访问
//        preloadOffset = PRELOAD_OFFSET
//        )
//
//        swipeRefresh.setOnRefreshListener {
//            swipeRefresh.isRefreshing = true
//            viewModel.refresh()
//        }
//    }
//
//    private fun setupObservers() {
//        val swipeRefresh: SwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout)
//        val fullScreenError: View = findViewById(R.id.full_screen_error_view)
//        val emptyView: View = findViewById(R.id.empty_view)
//        val errorTextView: TextView = findViewById(R.id.error_text_view)
//
//        fullScreenError.setOnClickListener { viewModel.refresh() }
//
//        lifecycleScope.launch {
//            repeatOnLifecycle(Lifecycle.State.STARTED) {
//                launch {
//                    viewModel.items.collect { list ->
//                        articleAdapter.submitList(list)
//                        loadStateAdapter.isDataEmpty = list.isEmpty()
//                    }
//                }
//                launch {
//                    viewModel.loadState.collect { state ->
//                        loadStateAdapter.loadState = state
//                        if (state !is LoadState.Loading) swipeRefresh.isRefreshing = false
//
//                        val isListEffectivelyEmpty = articleAdapter.currentList.isEmpty() || articleAdapter.currentList.all { it is ListItem.Placeholder }
//
//                        fullScreenError.isVisible = isListEffectivelyEmpty && state is LoadState.Error
//                        if (state is LoadState.Error) {
//                            errorTextView.text = when(state.error) {
//                                is PagingError.Network -> "网络连接失败，请检查设置"
//                                is PagingError.Server -> "服务器开小差了 (Code: ${state.error.code})"
//                                is PagingError.Unknown -> "发生未知错误"
//                            }
//                        }
//                        emptyView.isVisible = isListEffectivelyEmpty && state is LoadState.End
//                    }
//                }
//            }
//        }
//    }
//}
// endregion

/*
// region ==================== 6. 单元测试建议 (PagingHelperTest) ====================
// 位置：`src/test/java/com/example/custompaging/production/`
// 依赖：`testImplementation "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3"`
// 完整的测试用例代码在之前的讨论中已经提供，此处不再重复，仅作结构示意。
// endregion
*/

/*
// region ==================== 7. 相关的XML布局文件 ====================

// --- res/layout/activity_main.xml ---
<?xml version="1.0" encoding="utf-8"?>
<androidx.constraintlayout.widget.ConstraintLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:id="@+id/main"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipe_refresh_layout"
        android:layout_width="match_parent"
        android:layout_height="match_parent">

        <androidx.recyclerview.widget.RecyclerView
            android:id="@+id/recycler_view"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:scrollbars="vertical"
            app:layoutManager="androidx.recyclerview.widget.LinearLayoutManager" />
    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

    <LinearLayout
        android:id="@+id/full_screen_error_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent">

        <TextView
            android:id="@+id/error_text_view"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginBottom="16dp" />

        <Button
            android:id="@+id/error_retry_button"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="重试" />
    </LinearLayout>

    <TextView
        android:id="@+id/empty_view"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="这里什么都没有"
        android:visibility="gone"
        app:layout_constraintBottom_toBottomOf="parent"
        app:layout_constraintEnd_toEndOf="parent"
        app:layout_constraintStart_toStartOf="parent"
        app:layout_constraintTop_toTopOf="parent" />
</androidx.constraintlayout.widget.ConstraintLayout>

// --- res/layout/list_item_article.xml ---
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:paddingTop="12dp"
    android:paddingBottom="12dp"
    android:background="?android:attr/selectableItemBackground"
    android:clickable="true"
    android:focusable="true">

    <TextView
        android:id="@+id/article_title"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:textColor="@android:color/black"
        android:textStyle="bold"
        tools:text="ID: 123 - This is a sample article title" />

    <TextView
        android:id="@+id/article_subtitle"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textColor="@android:color/darker_gray"
        android:textSize="14sp"
        tools:text="This is a subtitle or a short description of the article."/>

</LinearLayout>

// --- res/layout/list_item_placeholder.xml (需要 com.facebook.shimmer:shimmer 依赖) ---
<com.facebook.shimmer.ShimmerFrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">
        <View android:layout_width="match_parent" android:layout_height="20dp" android:background="#E0E0E0"/>
        <View android:layout_width="150dp" android:layout_height="20dp" android:layout_marginTop="8dp" android:background="#E0E0E0"/>
    </LinearLayout>
</com.facebook.shimmer.ShimmerFrameLayout>

// --- res/layout/list_item_footer.xml ---
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:gravity="center"
    android:padding="16dp">

    <!-- 加载中状态：显示一个进度条 -->
    <ProgressBar
        android:id="@+id/footer_progress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:visibility="gone"
        tools:visibility="visible" />

    <!-- 加载失败状态：显示一个重试按钮 -->
    <Button
        android:id="@+id/footer_retry_button"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="加载失败，点击重试"
        android:visibility="gone"
        tools:visibility="gone" />

    <!-- 没有更多了状态：显示一段提示文字 -->
    <TextView
        android:id="@+id/footer_end_text"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="--- 我是有底线的 ---"
        android:textColor="@android:color/darker_gray"
        android:textSize="14sp"
        android:visibility="gone"
        tools:visibility="gone" />

</LinearLayout>

// endregion
*/