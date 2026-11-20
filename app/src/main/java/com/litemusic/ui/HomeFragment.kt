package com.litemusic.ui

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.litemusic.Api
import com.litemusic.ImageLoader
import com.litemusic.Playlist
import com.litemusic.Prefs
import com.litemusic.R
import com.litemusic.Song
import com.litemusic.player.PlayerService
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 首页。
 *
 * 头部：每日推荐 + 私人FM（横屏等宽并排，竖屏上下堆叠）。
 * 横屏：下方分类条 + ViewPager2 内容页，切换动画与「我的」分类条保持一致；
 * 竖屏：一个分类按钮，点击弹出选择框（与「我的音乐库」一致）。
 */
class HomeFragment : Fragment() {

    /** 分类取数方式 */
    private enum class CatKind { ALL, RECOMMEND, TOPLISTS, TAG }

    /** [tag] 为歌单广场的 cat 参数，仅 ALL / TAG 使用 */
    private data class Category(val label: Int, val kind: CatKind, val tag: String = "")

    private val categories = listOf(
        Category(R.string.cat_all, CatKind.ALL, "全部"),
        Category(R.string.cat_recommend, CatKind.RECOMMEND),
        Category(R.string.cat_official, CatKind.TAG, "官方"),
        Category(R.string.cat_toplists, CatKind.TOPLISTS),
        Category(R.string.cat_western, CatKind.TAG, "欧美"),
        Category(R.string.cat_pop, CatKind.TAG, "流行"),
        Category(R.string.cat_rock, CatKind.TAG, "摇滚"),
        Category(R.string.cat_electronic, CatKind.TAG, "电子"),
        Category(R.string.cat_rap, CatKind.TAG, "说唱"),
        Category(R.string.cat_acg, CatKind.TAG, "ACG"),
    )

    private val catCount: Int get() = categories.size

    // ---- 竖屏列表状态 ----
    private var activeCat = 0
    private var playlists: List<Playlist> = emptyList()
    private var squareOffset = 0
    private var squareMore = true
    private var loadingMore = false
    private var recycler: RecyclerView? = null

    private var spanCount = 2

    // ---- 横屏分类条状态 ----
    private var pager: ViewPager2? = null
    private val pageAdapters = HashMap<Int, CatPageAdapter>()
    private var tabItems: LinearLayout? = null
    private var tabIndicator: View? = null
    private var tabCenters = FloatArray(0)
    private var tabActiveColor = 0
    private var tabInactiveColor = 0
    private var currentTab = 0
    private var dragProgress = 0f
    private var fakeDragging = false
    private var tapAnimating = false
    private var tapFrom = 0f
    private var tapTo = 0f
    private var transitionActive = false
    private var pageScrollToken = 0
    private var appBar: AppBarLayout? = null
    private var appBarOffset = 0
    private var appBarRange = 1
    private var appBarOffsetListener: AppBarLayout.OnOffsetChangedListener? = null
    /** 等待头部折叠完成后再切页 */
    private var pendingScrollIndex = -1
    private val pageScrollInterpolator =
        androidx.core.view.animation.PathInterpolatorCompat.create(0.05f, 0.7f, 0.1f, 1f)

    /** 每日推荐首曲封面与整份列表 */
    private var dailyCoverUrl: String? = null
    private var dailySongs: List<Song> = emptyList()

    // ---- 私人FM ----
    private var fmSongs: List<Song> = emptyList()
    private var fmIndex = 0
    private var fmLoading = false
    private var header: HeaderVH? = null

    private val isLandscape: Boolean
        get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private val playerListener = object : PlayerService.Listener {
        override fun onSongChanged(song: Song?) {
            val svc = PlayerService.instance ?: return
            if (fmSongs.isEmpty() || svc.queue !== fmSongs) return
            val i = fmSongs.indexOfFirst { it.id == song?.id }
            if (i >= 0) {
                fmIndex = i
                refreshFm()
            }
        }

        override fun onPlayStateChanged(playing: Boolean) = refreshFm()

        override fun onProgress(positionMs: Int, durationMs: Int) = Unit
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, s: Bundle?): View {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val widthDp = resources.displayMetrics.widthPixels / resources.displayMetrics.density
        spanCount = (widthDp / 180).toInt().coerceAtLeast(2)
        if (isLandscape) setupLandscape(view) else setupPortrait(view)
        PlayerService.listeners.add(playerListener)
        loadDaily()
        if (Prefs.loggedIn) loadFm()
    }

    override fun onDestroyView() {
        PlayerService.listeners.remove(playerListener)
        appBarOffsetListener?.let { appBar?.removeOnOffsetChangedListener(it) }
        appBarOffsetListener = null
        pendingScrollIndex = -1
        header = null
        recycler = null
        pager = null
        tabItems = null
        tabIndicator = null
        pageAdapters.clear()
        pageScrollToken++
        super.onDestroyView()
    }

    // ---------- 竖屏 ----------

    private fun setupPortrait(view: View) {
        val rv = view.findViewById<RecyclerView>(R.id.recycler)
        recycler = rv
        val lm = GridLayoutManager(requireContext(), spanCount)
        lm.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            // 头部与底部"加载更多"独占整行
            override fun getSpanSize(position: Int): Int =
                if (position == 0 || position == (rv.adapter?.itemCount ?: 0) - 1) spanCount else 1
        }
        rv.layoutManager = lm
        rv.adapter = HomeAdapter()
        loadPortrait()
    }

    private val portraitPaginated: Boolean
        get() = categories[activeCat].kind.let { it == CatKind.ALL || it == CatKind.TAG }

    private fun loadPortrait() {
        val cat = categories[activeCat]
        when (cat.kind) {
            CatKind.RECOMMEND -> Api.personalized(24) { r ->
                if (!isAdded) return@personalized
                r.onSuccess { list ->
                    playlists = list
                    squareMore = false
                    recycler?.adapter?.notifyDataSetChanged()
                }.onFailure { showError(it) }
            }

            CatKind.TOPLISTS -> Api.toplists { r ->
                if (!isAdded) return@toplists
                r.onSuccess { list ->
                    playlists = list
                    squareMore = false
                    recycler?.adapter?.notifyDataSetChanged()
                }.onFailure { showError(it) }
            }

            CatKind.ALL, CatKind.TAG -> Api.playlistByCat(cat.tag, 24, 0) { r ->
                if (!isAdded) return@playlistByCat
                r.onSuccess { (list, more) ->
                    squareOffset = list.size
                    squareMore = more
                    playlists = list
                    recycler?.adapter?.notifyDataSetChanged()
                }.onFailure { showError(it) }
            }
        }
    }

    /** 竖屏：分类按钮弹出单选框 */
    private fun showCategoryDialog() {
        val labels = categories.map { getString(it.label) }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.discover)
            .setSingleChoiceItems(labels, activeCat) { dialog, which ->
                dialog.dismiss()
                if (which == activeCat) return@setSingleChoiceItems
                activeCat = which
                playlists = emptyList()
                squareOffset = 0
                squareMore = true
                updateCategoryButton()
                recycler?.adapter?.notifyDataSetChanged()
                loadPortrait()
            }
            .show()
    }

    private fun updateCategoryButton() {
        val b = header?.categoryButton ?: return
        b.visibility = if (isLandscape) View.GONE else View.VISIBLE
        b.text = getString(categories[activeCat].label)
    }

    private fun loadMorePortrait() {
        if (loadingMore || !squareMore || !portraitPaginated) return
        loadingMore = true
        refreshFooter()
        Api.playlistByCat(categories[activeCat].tag, 24, squareOffset) { r ->
            if (!isAdded) return@playlistByCat
            loadingMore = false
            r.onSuccess { (list, more) ->
                squareOffset += list.size
                squareMore = more
                val existing = playlists.mapTo(HashSet()) { it.id }
                val fresh = list.filterNot { existing.contains(it.id) }
                if (fresh.isNotEmpty()) {
                    val start = playlists.size + 1
                    playlists = playlists + fresh
                    recycler?.adapter?.notifyItemRangeInserted(start, fresh.size)
                }
                refreshFooter()
            }.onFailure {
                refreshFooter()
                showError(it)
            }
        }
    }

    private fun refreshFooter() {
        val a = recycler?.adapter ?: return
        if (a.itemCount > 0) a.notifyItemChanged(a.itemCount - 1)
    }

    // ---------- 横屏 ----------

    private fun setupLandscape(view: View) {
        val ab = view.findViewById<AppBarLayout>(R.id.home_app_bar)
        appBar = ab
        // 头部折叠完成后才触发切页：先让分类条滑到顶部，再滚动页面
        appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { layout, offset ->
            appBarOffset = offset
            appBarRange = layout.totalScrollRange
            if (pendingScrollIndex >= 0 && layout.totalScrollRange > 0 &&
                -offset >= layout.totalScrollRange
            ) {
                startPendingPageScroll()
            }
        }.also { ab.addOnOffsetChangedListener(it) }

        val h = HeaderVH(view)
        header = h
        bindHeader(h)

        val p = view.findViewById<ViewPager2>(R.id.home_pager)
        pager = p
        p.orientation = ViewPager2.ORIENTATION_HORIZONTAL
        p.isUserInputEnabled = false
        p.adapter = CatPagerAdapter()
        p.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
                dragProgress = position + positionOffset
                moveIndicatorByPage(position, positionOffset)
                if (tapAnimating) {
                    val span = tapTo - tapFrom
                    if (span != 0f) {
                        val progress = ((position + positionOffset) - tapFrom) / span
                        val v = progress.coerceIn(0f, 1f)
                        val scale = 1f + 0.12f * kotlin.math.sin(Math.PI * v).toFloat()
                        tabIndicator?.scaleX = scale
                        tabIndicator?.scaleY = scale
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                currentTab = position
                if (!transitionActive) applyTabColors(position)
            }

            override fun onPageScrollStateChanged(state: Int) {
                if (state == ViewPager2.SCROLL_STATE_IDLE) finishTransition()
            }
        })

        val bar = view.findViewById<View>(R.id.home_tabs)
        tabItems = view.findViewById(R.id.home_tab_items)
        tabIndicator = view.findViewById(R.id.home_tab_indicator)
        tabActiveColor = MaterialColors.getColor(bar, com.google.android.material.R.attr.colorPrimary, 0)
        tabInactiveColor =
            MaterialColors.getColor(bar, com.google.android.material.R.attr.colorOnSurfaceVariant, 0)
        buildTabs()
        updateCategoryButton()
        bar.post {
            refreshTabCenters()
            moveIndicatorByPage(p.currentItem, 0f)
            applyTabColors(p.currentItem)
        }
        bar.addOnLayoutChangeListener { _, left, _, right, _, oldLeft, _, oldRight, _ ->
            if (right - left != oldRight - oldLeft) {
                refreshTabCenters()
                moveIndicatorByPage(p.currentItem, 0f)
            }
        }
    }

    /** 分类标签：等宽铺满，点击/横向拖动切换，与「我的」分类条同款 */
    private fun buildTabs() {
        val items = tabItems ?: return
        items.removeAllViews()
        val bar = items.parent as? View ?: return
        for (i in categories.indices) {
            val tv = TextView(requireContext()).apply {
                text = getString(categories[i].label)
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setTextColor(tabInactiveColor)
                TextViewCompat.setTextAppearance(
                    this, com.google.android.material.R.style.TextAppearance_Material3_TitleSmall
                )
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                setOnClickListener { selectTab(i) }
                setOnTouchListener(tabTouchListener(bar))
            }
            items.addView(tv)
        }
    }

    private fun applyTabColors(selected: Int) {
        val items = tabItems ?: return
        for (i in 0 until items.childCount) {
            (items.getChildAt(i) as? TextView)?.setTextColor(
                if (i == selected) tabActiveColor else tabInactiveColor
            )
        }
    }

    private fun refreshTabCenters() {
        val items = tabItems ?: return
        if (items.childCount < catCount) return
        val centers = FloatArray(catCount)
        for (i in 0 until catCount) {
            val child = items.getChildAt(i)
            centers[i] = child.left + child.width / 2f
        }
        tabCenters = centers
    }

    private fun moveIndicatorByPage(position: Int, offset: Float) {
        val indicator = tabIndicator ?: return
        if (tabCenters.size < catCount || indicator.width == 0) return
        val from = position.coerceIn(0, catCount - 1)
        val to = (from + 1).coerceAtMost(catCount - 1)
        val center = tabCenters[from] + (tabCenters[to] - tabCenters[from]) * offset
        indicator.translationX = center - indicator.width / 2f
    }

    private fun selectTab(index: Int) {
        val p = pager ?: return
        if (p.isFakeDragging) {
            p.endFakeDrag()
            fakeDragging = false
        }
        if (index == p.currentItem) return
        transitionActive = true
        tapFrom = dragProgress
        tapTo = index.toFloat()
        tapAnimating = true
        collapseThenScroll(index, p)
    }

    /** 先折叠头部（分类条吸顶），再让页面平滑滚动 */
    private fun collapseThenScroll(index: Int, p: ViewPager2) {
        val bar = appBar
        if (bar == null || appBarRange <= 0 || -appBarOffset >= appBarRange) {
            startPageScroll(index, p)
            return
        }
        pendingScrollIndex = index
        bar.setExpanded(false, true)
        // 兜底：折叠回调未到达完全收起时也能继续切页
        bar.postDelayed({
            if (pendingScrollIndex == index) startPendingPageScroll()
        }, 500)
    }

    private fun startPendingPageScroll() {
        val index = pendingScrollIndex
        if (index < 0) return
        pendingScrollIndex = -1
        val p = pager ?: return
        startPageScroll(index, p)
    }

    /** 固定时长的位移动画切页，与「我的」一致 */
    private fun startPageScroll(index: Int, p: ViewPager2) {
        if (!isAdded || !p.isAttachedToWindow) return
        val rv = p.getChildAt(0) as? RecyclerView ?: run {
            p.setCurrentItem(index, false)
            return
        }
        val token = ++pageScrollToken
        val width = rv.width.coerceAtLeast(1)
        val fromOffset = rv.computeHorizontalScrollOffset()
        val toOffset = index * width
        val pages = abs(toOffset - fromOffset).toFloat() / width
        val duration = (170f + 50f * pages).toLong().coerceIn(200L, 420L)
        val startTime = SystemClock.uptimeMillis()
        val runner = object : Runnable {
            override fun run() {
                if (token != pageScrollToken || !isAdded || pager !== p || !p.isAttachedToWindow) return
                val t = ((SystemClock.uptimeMillis() - startTime).toFloat() / duration).coerceIn(0f, 1f)
                val target = fromOffset + (toOffset - fromOffset) *
                    pageScrollInterpolator.getInterpolation(t)
                rv.scrollBy((target - rv.computeHorizontalScrollOffset()).toInt(), 0)
                if (t < 1f) {
                    p.postOnAnimation(this)
                } else {
                    p.setCurrentItem(index, false)
                    finishTransition()
                }
            }
        }
        p.postOnAnimation(runner)
    }

    private fun finishTransition() {
        if (!transitionActive && !tapAnimating) return
        transitionActive = false
        if (tapAnimating) {
            tapAnimating = false
            tabIndicator?.animate()?.cancel()
            tabIndicator?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(150)?.start()
        }
        currentTab = pager?.currentItem ?: currentTab
        applyTabColors(currentTab)
    }

    /** 分类条横向拖动：fakeDrag 让内容页跟手滚动 */
    private fun tabTouchListener(bar: View): View.OnTouchListener {
        val slop = ViewConfiguration.get(requireContext()).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var lastX = 0f
        var dragging = false
        return View.OnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    lastX = e.rawX
                    dragging = false
                    false
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragging && abs(dx) > slop && abs(dx) > abs(dy)) {
                        val p = pager
                        pageScrollToken++
                        if (p != null && p.beginFakeDrag()) {
                            dragging = true
                            fakeDragging = true
                            tapAnimating = false
                            transitionActive = true
                            dragProgress = p.currentItem.toFloat()
                            lastX = e.rawX
                            tabIndicator?.animate()?.cancel()
                            tabIndicator?.scaleX = 1.15f
                            tabIndicator?.scaleY = 1.15f
                            bar.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                    if (dragging) {
                        val delta = e.rawX - lastX
                        lastX = e.rawX
                        if (delta != 0f) {
                            val spacing = if (tabCenters.size > 1) tabCenters[1] - tabCenters[0] else 1f
                            val scale = if (spacing > 0f) bar.width / spacing else 1f
                            pager?.fakeDragBy(-delta * scale)
                        }
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        dragging = false
                        bar.parent?.requestDisallowInterceptTouchEvent(false)
                        endTabDrag(true)
                        true
                    } else {
                        false
                    }
                }

                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        dragging = false
                        bar.parent?.requestDisallowInterceptTouchEvent(false)
                        endTabDrag(false)
                        true
                    } else {
                        false
                    }
                }

                else -> false
            }
        }
    }

    private fun endTabDrag(snap: Boolean) {
        if (!fakeDragging) return
        fakeDragging = false
        tabIndicator?.animate()?.cancel()
        tabIndicator?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(120)?.start()
        val p = pager ?: return
        if (!p.isAttachedToWindow) return
        if (!snap) {
            p.endFakeDrag()
            p.setCurrentItem(currentTab, true)
            finishTransition()
            return
        }
        val nearest = dragProgress.roundToInt().coerceIn(0, catCount - 1)
        val start = SystemClock.uptimeMillis()
        val freeze = object : Runnable {
            override fun run() {
                if (!isAdded || pager !== p || !p.isAttachedToWindow) {
                    if (p.isFakeDragging) p.endFakeDrag()
                    finishTransition()
                    return
                }
                if (SystemClock.uptimeMillis() - start < 120) {
                    p.fakeDragBy(0f)
                    p.postDelayed(this, 20)
                } else {
                    p.endFakeDrag()
                    p.post {
                        if (p.currentItem != nearest) p.setCurrentItem(nearest, true)
                        finishTransition()
                    }
                }
            }
        }
        freeze.run()
    }

    /** 横屏内容页：每个分类一个 RecyclerView，独立取数与分页 */
    private inner class CatPagerAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = catCount

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val rv = RecyclerView(requireContext()).apply {
                layoutManager = GridLayoutManager(requireContext(), spanCount)
                clipToPadding = false
                setPadding(0, dp(8), 0, dp(96))
                // ViewPager2 要求页面铺满整页
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
            return object : RecyclerView.ViewHolder(rv) {}
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val rv = holder.itemView as RecyclerView
            val adapter = pageAdapters.getOrPut(position) { CatPageAdapter(position) }
            if (rv.adapter !== adapter) rv.adapter = adapter
            adapter.ensureLoaded()
        }
    }

    private inner class CatPageAdapter(private val catIndex: Int) :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private var items: List<Playlist> = emptyList()
        private var offset = 0
        private var more = true
        private var loading = false
        private var loaded = false

        private val paginated: Boolean
            get() = categories[catIndex].kind.let { it == CatKind.ALL || it == CatKind.TAG }

        private val showFooter: Boolean
            get() = paginated && (more || loading || items.isEmpty())

        fun ensureLoaded() {
            if (loaded || loading) return
            loading = true
            notifyDataSetChanged()
            val cat = categories[catIndex]
            when (cat.kind) {
                CatKind.RECOMMEND -> Api.personalized(24) { r ->
                    apply(r.map { list -> list to false })
                }

                CatKind.TOPLISTS -> Api.toplists { r ->
                    apply(r.map { list -> list to false })
                }

                CatKind.ALL, CatKind.TAG -> Api.playlistByCat(cat.tag, 24, 0) { r ->
                    apply(r)
                }
            }
        }

        private fun apply(r: Result<Pair<List<Playlist>, Boolean>>) {
            loading = false
            if (!isAdded) return
            r.onSuccess { (list, moreFlag) ->
                loaded = true
                items = list
                offset = list.size
                more = moreFlag
                notifyDataSetChanged()
            }.onFailure {
                notifyDataSetChanged()
                showError(it)
            }
        }

        private fun loadMore() {
            if (loading || !more) return
            loading = true
            notifyItemChanged(items.size)
            Api.playlistByCat(categories[catIndex].tag, 24, offset) { r ->
                loading = false
                if (!isAdded) return@playlistByCat
                r.onSuccess { (list, moreFlag) ->
                    offset += list.size
                    more = moreFlag
                    val existing = items.mapTo(HashSet()) { it.id }
                    items = items + list.filterNot { existing.contains(it.id) }
                    notifyDataSetChanged()
                }.onFailure {
                    notifyDataSetChanged()
                    showError(it)
                }
            }
        }

        override fun getItemCount(): Int = items.size + if (showFooter) 1 else 0

        override fun getItemViewType(position: Int): Int =
            if (showFooter && position == items.size) 1 else 0

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(parent.context)
            return if (viewType == 1) {
                LoadMoreVH(inf.inflate(R.layout.item_load_more, parent, false))
            } else {
                PlaylistVH(inf.inflate(R.layout.item_playlist, parent, false))
            }
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            if (h is LoadMoreVH) {
                val b = h.button
                when {
                    loading -> { b.text = getString(R.string.loading); b.isEnabled = false }
                    !more -> { b.text = getString(R.string.no_more); b.isEnabled = false }
                    else -> { b.text = getString(R.string.load_more); b.isEnabled = true }
                }
                b.setOnClickListener { loadMore() }
            } else if (h is PlaylistVH) {
                val p = items[pos]
                h.name.text = p.name
                h.count.text = formatCount(p.playCount)
                ImageLoader.load(h.cover, p.cover, 300)
                h.itemView.setOnClickListener {
                    startActivity(PlaylistActivity.launch(requireContext(), p.id, p.name))
                }
            }
        }
    }

    // ---------- 竖屏适配器 ----------

    private inner class HomeAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount(): Int = 1 + playlists.size + 1

        override fun getItemViewType(position: Int): Int = when (position) {
            0 -> 0
            itemCount - 1 -> 2
            else -> 1
        }

        override fun onCreateViewHolder(group: ViewGroup, type: Int): RecyclerView.ViewHolder {
            val inf = LayoutInflater.from(group.context)
            return when (type) {
                0 -> HeaderVH(inf.inflate(R.layout.item_home_header, group, false))
                2 -> LoadMoreVH(inf.inflate(R.layout.item_load_more, group, false))
                else -> PlaylistVH(inf.inflate(R.layout.item_playlist, group, false))
            }
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
            if (h is HeaderVH) {
                header = h
                bindHeader(h)
            } else if (h is LoadMoreVH) {
                val b = h.button
                if (!portraitPaginated) {
                    b.visibility = View.GONE
                    return
                }
                b.visibility = View.VISIBLE
                when {
                    loadingMore -> { b.text = getString(R.string.loading); b.isEnabled = false }
                    !squareMore -> { b.text = getString(R.string.no_more); b.isEnabled = false }
                    else -> { b.text = getString(R.string.load_more); b.isEnabled = true }
                }
                b.setOnClickListener { loadMorePortrait() }
            } else if (h is PlaylistVH) {
                val p = playlists[pos - 1]
                h.name.text = p.name
                h.count.text = formatCount(p.playCount)
                ImageLoader.load(h.cover, p.cover, 300)
                h.itemView.setOnClickListener {
                    startActivity(PlaylistActivity.launch(requireContext(), p.id, p.name))
                }
            }
        }
    }

    // ---------- 头部 ----------

    private fun bindHeader(h: HeaderVH) {
        // 横屏等宽并排；竖屏上下堆叠
        val lpDaily = h.cardDaily.layoutParams as LinearLayout.LayoutParams
        val lpFm = h.cardFm.layoutParams as LinearLayout.LayoutParams
        if (isLandscape) {
            h.cardsRow.orientation = LinearLayout.HORIZONTAL
            lpDaily.width = 0
            lpDaily.weight = 1f
            lpDaily.height = dp(156)
            lpFm.width = 0
            lpFm.weight = 1f
            lpFm.height = dp(156)
            lpFm.marginStart = dp(12)
            lpFm.topMargin = 0
        } else {
            h.cardsRow.orientation = LinearLayout.VERTICAL
            lpDaily.width = LinearLayout.LayoutParams.MATCH_PARENT
            lpDaily.weight = 0f
            lpDaily.height = dp(128)
            lpFm.width = LinearLayout.LayoutParams.MATCH_PARENT
            lpFm.weight = 0f
            lpFm.height = dp(128)
            lpFm.marginStart = 0
            lpFm.topMargin = dp(12)
        }
        h.cardDaily.layoutParams = lpDaily
        h.cardFm.layoutParams = lpFm

        // 封面保持正方形：横屏 132dp，竖屏 104dp
        val coverSize = dp(if (isLandscape) 132 else 104)
        h.fmCover.layoutParams = (h.fmCover.layoutParams as LinearLayout.LayoutParams).apply {
            width = coverSize
            height = coverSize
        }

        ImageLoader.load(h.dailyCover, dailyCoverUrl, 600)
        h.cardDaily.setOnClickListener {
            if (!Prefs.loggedIn) needLogin() else startActivity(PlaylistActivity.daily(requireContext()))
        }
        h.dailyPlay.setOnClickListener { playDaily() }

        h.cardFm.setOnClickListener { toggleFm() }
        h.fmPlay.setOnClickListener { toggleFm() }
        h.fmNext.setOnClickListener { nextFm(dislike = false) }
        h.fmDislike.setOnClickListener { nextFm(dislike = true) }

        h.categoryButton.setOnClickListener { showCategoryDialog() }
        updateCategoryButton()
        refreshFm()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private class HeaderVH(v: View) : RecyclerView.ViewHolder(v) {
        val cardsRow: LinearLayout = v.findViewById(R.id.cards_row)
        val cardDaily: MaterialCardView = v.findViewById(R.id.card_daily)
        val dailyCover: ImageView = v.findViewById(R.id.daily_cover)
        val dailyPlay: ImageButton = v.findViewById(R.id.daily_play)
        val cardFm: MaterialCardView = v.findViewById(R.id.card_fm)
        val fmCover: ImageView = v.findViewById(R.id.fm_cover)
        val fmTitle: TextView = v.findViewById(R.id.fm_title)
        val fmArtist: TextView = v.findViewById(R.id.fm_artist)
        val fmPlay: ImageButton = v.findViewById(R.id.fm_play)
        val fmNext: ImageButton = v.findViewById(R.id.fm_next)
        val fmDislike: ImageButton = v.findViewById(R.id.fm_dislike)
        val categoryButton: MaterialButton = v.findViewById(R.id.category_button)
    }

    private class LoadMoreVH(v: View) : RecyclerView.ViewHolder(v) {
        val button: MaterialButton = v.findViewById(R.id.btn_load_more)
    }

    private class PlaylistVH(v: View) : RecyclerView.ViewHolder(v) {
        val cover: ImageView = v.findViewById(R.id.cover)
        val name: TextView = v.findViewById(R.id.name)
        val count: TextView = v.findViewById(R.id.count)
    }

    // ---------- 每日推荐 ----------

    private fun loadDaily() {
        Api.recommendSongs { r ->
            if (!isAdded) return@recommendSongs
            val list = r.getOrNull() ?: return@recommendSongs
            if (list.isEmpty()) return@recommendSongs
            dailySongs = list
            dailyCoverUrl = list.first().cover
            refreshDaily()
        }
    }

    private fun refreshDaily() {
        val h = header ?: return
        ImageLoader.load(h.dailyCover, dailyCoverUrl, 600)
        if (recycler?.adapter != null) recycler?.adapter?.notifyItemChanged(0)
    }

    private fun playDaily() {
        if (!Prefs.loggedIn) {
            needLogin()
            return
        }
        if (dailySongs.isNotEmpty()) {
            PlayerService.play(requireContext(), dailySongs, 0)
        } else {
            Api.recommendSongs { r ->
                if (!isAdded) return@recommendSongs
                val list = r.getOrNull() ?: return@recommendSongs
                if (list.isEmpty()) return@recommendSongs
                dailySongs = list
                PlayerService.play(requireContext(), list, 0)
            }
        }
    }

    // ---------- 私人FM ----------

    private fun loadFm(then: (() -> Unit)? = null) {
        if (fmLoading) return
        fmLoading = true
        Api.personalFm { r ->
            fmLoading = false
            if (!isAdded) return@personalFm
            val list = r.getOrNull() ?: return@personalFm
            if (list.isEmpty()) return@personalFm
            fmSongs = list
            fmIndex = 0
            refreshFm()
            then?.invoke()
        }
    }

    private val fmNow: Song?
        get() {
            val svc = PlayerService.instance
            if (svc != null && fmSongs.isNotEmpty() && svc.queue === fmSongs) return svc.currentSong
            return fmSongs.getOrNull(fmIndex)
        }

    private val fmPlaying: Boolean
        get() {
            val svc = PlayerService.instance ?: return false
            return fmSongs.isNotEmpty() && svc.queue === fmSongs && svc.playing
        }

    private fun toggleFm() {
        if (!Prefs.loggedIn) {
            needLogin()
            return
        }
        val svc = PlayerService.instance
        if (svc != null && fmSongs.isNotEmpty() && svc.queue === fmSongs) {
            svc.togglePlay()
            return
        }
        if (fmSongs.isEmpty()) loadFm { startFm() } else startFm()
    }

    private fun startFm() {
        if (fmSongs.isEmpty()) return
        PlayerService.play(requireContext(), fmSongs, fmIndex.coerceIn(0, fmSongs.size - 1))
    }

    private fun nextFm(dislike: Boolean) {
        if (!Prefs.loggedIn) {
            needLogin()
            return
        }
        if (fmSongs.isEmpty()) {
            loadFm { nextFm(dislike) }
            return
        }
        if (dislike) {
            val rest = fmSongs.toMutableList()
            rest.removeAt(fmIndex.coerceIn(0, rest.size - 1))
            fmSongs = rest
            if (fmSongs.isEmpty()) {
                fmIndex = 0
                loadFm { startFm() }
                return
            }
            if (fmIndex > fmSongs.size - 1) fmIndex = 0
            startFm()
            return
        }
        if (fmIndex + 1 >= fmSongs.size) {
            loadFm { startFm() }
        } else {
            fmIndex++
            startFm()
        }
    }

    private fun refreshFm() {
        val h = header ?: return
        val song = fmNow
        h.fmTitle.text = song?.name ?: getString(R.string.personal_fm)
        h.fmArtist.text = song?.artists ?: ""
        ImageLoader.load(h.fmCover, song?.cover, 240)
        h.fmPlay.setImageResource(if (fmPlaying) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun needLogin() {
        if (!isAdded) return
        Toast.makeText(requireContext(), R.string.not_logged_in, Toast.LENGTH_SHORT).show()
        startActivity(Intent(requireContext(), LoginActivity::class.java))
    }

    private fun showError(t: Throwable) {
        if (!isAdded) return
        Snackbar.make(requireView(), t.message ?: getString(R.string.error_network), Snackbar.LENGTH_SHORT).show()
    }

    companion object {
        fun formatCount(n: Long): String = when {
            n >= 100_000_000 -> String.format("%.1f亿", n / 100_000_000.0)
            n >= 10_000 -> "${n / 10_000}万"
            else -> n.toString()
        }
    }
}
