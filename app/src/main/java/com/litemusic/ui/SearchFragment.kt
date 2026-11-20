package com.litemusic.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.litemusic.Api
import com.litemusic.HistoryStore
import com.litemusic.ImageLoader
import com.litemusic.Playlist
import com.litemusic.Prefs
import com.litemusic.R
import com.litemusic.Song
import com.litemusic.player.PlayerService

/**
 * 搜索页：首页（选择搜索对象与筛选范围）↔ 结果页。
 * 输入框为空时回车/搜索即返回首页。
 */
class SearchFragment : Fragment() {

    private enum class Scope { ALL, LIKED, PLAYLISTS, ALBUMS }
    private enum class Type { SONG, PLAYLIST, ALBUM, ARTIST }

    private lateinit var input: TextInputEditText
    private lateinit var recycler: RecyclerView
    private lateinit var emptyTip: TextView
    private lateinit var typeGroup: ChipGroup
    private lateinit var scopeGroup: ChipGroup
    private lateinit var labelObject: View
    private lateinit var labelFilter: View
    private lateinit var labelHistory: TextView
    private lateinit var historyGroup: ChipGroup

    private var scope = Scope.ALL
    private var type = Type.SONG
    private var requestToken = 0
    private var lastRecordedKeyword = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_search, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        input = view.findViewById(R.id.search_input)
        recycler = view.findViewById(R.id.recycler)
        emptyTip = view.findViewById(R.id.empty_tip)
        typeGroup = view.findViewById(R.id.type_group)
        scopeGroup = view.findViewById(R.id.scope_group)
        labelObject = view.findViewById(R.id.label_search_object)
        labelFilter = view.findViewById(R.id.label_search_filter)
        labelHistory = view.findViewById(R.id.label_history)
        historyGroup = view.findViewById(R.id.history_group)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        // 点击搜索框以外的任何区域都会收起键盘，且不拦截列表点击
        (view as SearchRootLayout).apply {
            ignoreView = view.findViewById(R.id.search_layout)
            onOutsideTouchUp = { dismissSearchFocus() }
        }

        typeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            type = when (checkedIds.firstOrNull()) {
                R.id.chip_type_playlist -> Type.PLAYLIST
                R.id.chip_type_album -> Type.ALBUM
                R.id.chip_type_artist -> Type.ARTIST
                else -> Type.SONG
            }
            runSearch()
        }
        scopeGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            val newScope = when (checkedIds.firstOrNull()) {
                R.id.chip_scope_liked -> Scope.LIKED
                R.id.chip_scope_playlists -> Scope.PLAYLISTS
                R.id.chip_scope_albums -> Scope.ALBUMS
                else -> Scope.ALL
            }
            // 收藏内容都需要登录态，未登录时回退到全站
            if (newScope != Scope.ALL && !Prefs.loggedIn) {
                Toast.makeText(requireContext(), R.string.not_logged_in, Toast.LENGTH_SHORT).show()
                scopeGroup.check(R.id.chip_scope_all)
                return@setOnCheckedStateChangeListener
            }
            scope = newScope
            typeGroup.visibility = if (scope == Scope.ALL) View.VISIBLE else View.GONE
            runSearch()
        }
        input.setOnEditorActionListener { _, actionId, event ->
            // 软键盘「搜索」键，或硬件/外接键盘回车，都触发搜索
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                event?.keyCode == android.view.KeyEvent.KEYCODE_ENTER
            ) {
                doSearch()
                true
            } else false
        }

        showHome()
    }

    private fun keyword(): String = input.text?.toString()?.trim() ?: ""

    /** 输入为空时回车/搜索 → 回到搜索首页 */
    private fun doSearch() {
        val kw = keyword()
        if (kw.isEmpty()) showHome() else runSearch()
    }

    private fun showHome() {
        requestToken++
        if (!isAdded) return
        recycler.adapter = null
        renderHistory()
        emptyTip.text = getString(R.string.search_home_tip)
        emptyTip.visibility = if (Prefs.searchHistory().isEmpty()) View.VISIBLE else View.GONE
        setFiltersVisible(true)
    }

    /** 搜索历史：横向排列的小按钮，点击直接再次搜索 */
    private fun renderHistory() {
        if (!isAdded) return
        val list = Prefs.searchHistory()
        historyGroup.removeAllViews()
        val show = list.isNotEmpty()
        labelHistory.visibility = if (show) View.VISIBLE else View.GONE
        historyGroup.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) return
        val stroke = ColorStateList.valueOf(
            MaterialColors.getColor(historyGroup, com.google.android.material.R.attr.colorOutline, 0)
        )
        val fg = MaterialColors.getColor(
            historyGroup, com.google.android.material.R.attr.colorOnSurfaceVariant, 0
        )
        for (kw in list) {
            val chip = Chip(requireContext())
            chip.text = kw
            chip.isCheckable = false
            chip.isClickable = true
            // 与未选中的搜索对象一致：透明填充 + 描边
            chip.setChipBackgroundColor(ColorStateList.valueOf(Color.TRANSPARENT))
            chip.chipStrokeWidth = resources.displayMetrics.density
            chip.chipStrokeColor = stroke
            chip.setTextColor(fg)
            chip.isChipIconVisible = false
            chip.setOnClickListener {
                input.setText(kw)
                input.setSelection(kw.length)
                doSearch()
            }
            // 长按删除该条搜索历史
            chip.setOnLongClickListener {
                Prefs.removeSearch(kw)
                renderHistory()
                emptyTip.visibility =
                    if (Prefs.searchHistory().isEmpty()) View.VISIBLE else View.GONE
                Toast.makeText(requireContext(), R.string.search_history_removed, Toast.LENGTH_SHORT).show()
                true
            }
            historyGroup.addView(chip)
        }
    }

    private fun dismissSearchFocus() {
        if (!input.hasFocus()) return
        input.clearFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(input.windowToken, 0)
    }

    /** 「搜索对象」「筛选」只在首页显示，结果页隐藏 */
    private fun setFiltersVisible(visible: Boolean) {
        val v = if (visible) View.VISIBLE else View.GONE
        labelObject.visibility = v
        labelFilter.visibility = v
        scopeGroup.visibility = v
        typeGroup.visibility = if (visible && scope == Scope.ALL) View.VISIBLE else View.GONE
        if (!visible) {
            labelHistory.visibility = View.GONE
            historyGroup.visibility = View.GONE
        }
    }

    private fun runSearch() {
        if (!isAdded) return
        val kw = keyword()
        if (kw.isEmpty()) {
            showHome()
            return
        }
        if (scope != Scope.ALL && !Prefs.loggedIn) {
            Toast.makeText(requireContext(), R.string.not_logged_in, Toast.LENGTH_SHORT).show()
            showHome()
            return
        }
        if (kw != lastRecordedKeyword) {
            Prefs.recordSearch(kw)
            lastRecordedKeyword = kw
        }
        val token = ++requestToken
        recycler.adapter = null
        emptyTip.text = getString(R.string.loading)
        emptyTip.visibility = View.VISIBLE
        setFiltersVisible(false)

        when (scope) {
            Scope.ALL -> when (type) {
                Type.SONG -> Api.search(kw, 30, 0) { r -> onSongs(token, r) }
                Type.PLAYLIST -> Api.searchPlaylists(kw, 30, 0) { r -> onItems(token, Type.PLAYLIST, r) }
                Type.ALBUM -> Api.searchAlbums(kw, 30, 0) { r -> onItems(token, Type.ALBUM, r) }
                Type.ARTIST -> Api.searchArtists(kw, 30, 0) { r -> onItems(token, Type.ARTIST, r) }
            }
            Scope.LIKED -> searchLiked(kw, token)
            Scope.PLAYLISTS -> searchLocalPlaylists(kw, token)
            Scope.ALBUMS -> searchLocalAlbums(kw, token)
        }
    }

    private fun onSongs(token: Int, r: Result<List<Song>>) {
        if (!isAdded || token != requestToken) return
        r.onSuccess { showSongs(it) }.onFailure { showFailure(it) }
    }

    private fun onItems(token: Int, kind: Type, r: Result<List<Playlist>>) {
        if (!isAdded || token != requestToken) return
        r.onSuccess { showItems(it, kind) }.onFailure { showFailure(it) }
    }

    // ---- 本地收藏范围搜索 ----

    private fun searchLiked(kw: String, token: Int) {
        Api.likelist { r ->
            if (!isAdded || token != requestToken) return@likelist
            val ids = r.getOrNull()
            if (ids.isNullOrEmpty()) {
                if (r.isFailure) showFailure(r.exceptionOrNull()!!) else showSongs(emptyList())
                return@likelist
            }
            // 与"我喜欢的音乐"页一致，最多取 300 首，避免大量分页请求拖慢低配设备
            Api.songDetail(ids.take(300)) { r2 ->
                if (!isAdded || token != requestToken) return@songDetail
                r2.onSuccess { list -> showSongs(list.filter { matches(it, kw) }) }
                    .onFailure { showFailure(it) }
            }
        }
    }

    private fun searchLocalPlaylists(kw: String, token: Int) {
        val load = {
            Api.userPlaylist(Prefs.userId) { r ->
                if (!isAdded || token != requestToken) return@userPlaylist
                r.onSuccess { (_, list) ->
                    // 历史记录作为私人歌单固定放在最后
                    val merged = ArrayList<Playlist>(list)
                    merged.add(HistoryStore.playlist(requireContext()))
                    showItems(merged.filter { matches(it, kw) }, Type.PLAYLIST)
                }.onFailure { showFailure(it) }
            }
        }
        if (Prefs.userId == 0L) {
            // 老版本遗留 userId 缺失时先自愈
            Api.account { r ->
                if (!isAdded || token != requestToken) return@account
                if (r.isFailure) r.exceptionOrNull()?.let { showFailure(it) } else load()
            }
        } else {
            load()
        }
    }

    private fun searchLocalAlbums(kw: String, token: Int) {
        Api.albumSublist { r ->
            if (!isAdded || token != requestToken) return@albumSublist
            r.onSuccess { showItems(it.filter { matches(it, kw) }, Type.ALBUM) }
                .onFailure { showFailure(it) }
        }
    }

    // ---- 结果渲染 ----

    private fun showSongs(list: List<Song>) {
        renderEmptyState(list.isEmpty())
        recycler.adapter = if (list.isEmpty()) null else SongAdapter(list) { index ->
            PlayerService.play(requireContext(), list, index)
        }
    }

    private fun showItems(list: List<Playlist>, kind: Type) {
        val items = if (kind == Type.PLAYLIST) {
            val merged = ArrayList(list)
            // 全站/本地歌单搜索结果都允许搜到本地历史记录歌单
            if (merged.none { it.history }) {
                val history = HistoryStore.playlist(requireContext())
                if (matches(history, keyword())) merged.add(history)
            }
            merged
        } else {
            list
        }
        renderEmptyState(items.isEmpty())
        recycler.adapter = if (items.isEmpty()) null else ResultAdapter(items, kind) { p ->
            when (kind) {
                Type.PLAYLIST -> if (p.history) {
                    startActivity(HistoryActivity.launch(requireContext()))
                } else {
                    startActivity(PlaylistActivity.launch(requireContext(), p.id, p.name))
                }
                Type.ALBUM -> startActivity(PlaylistActivity.album(requireContext(), p.id, p.name))
                Type.ARTIST -> startActivity(ArtistActivity.launch(requireContext(), p.id, p.name))
                else -> {}
            }
        }
    }

    private fun renderEmptyState(empty: Boolean) {
        emptyTip.visibility = if (empty) View.VISIBLE else View.GONE
        if (empty) emptyTip.text = getString(R.string.search_no_result)
    }

    private fun showFailure(t: Throwable) {
        emptyTip.visibility = View.GONE
        Snackbar.make(recycler, t.message ?: getString(R.string.error_network), Snackbar.LENGTH_SHORT).show()
    }

    private fun matches(s: Song, kw: String): Boolean =
        s.name.contains(kw, true) || s.artists.contains(kw, true) || s.album.contains(kw, true)

    private fun matches(p: Playlist, kw: String): Boolean =
        p.name.contains(kw, true) || (p.subtitle?.contains(kw, true) == true)

    /** 歌单/专辑/歌手结果列表（复用歌曲行布局，隐藏红心） */
    private inner class ResultAdapter(
        private val items: List<Playlist>,
        private val kind: Type,
        private val onClick: (Playlist) -> Unit,
    ) : RecyclerView.Adapter<ResultAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val cover: ImageView = v.findViewById(R.id.cover)
            val name: TextView = v.findViewById(R.id.name)
            val artist: TextView = v.findViewById(R.id.artist)

            init {
                v.findViewById<View>(R.id.btn_like).visibility = View.GONE
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(h: VH, position: Int) {
            val p = items[position]
            h.name.text = p.name
            h.artist.text = when (kind) {
                Type.ALBUM -> listOfNotNull(
                    p.subtitle?.takeIf { it.isNotEmpty() },
                    getString(R.string.track_count, p.trackCount),
                ).joinToString(" · ")
                Type.ARTIST -> getString(R.string.album_count, p.albumCount)
                else -> if (p.history) getString(R.string.track_count, p.trackCount)
                else getString(R.string.playlist_meta, p.trackCount, HomeFragment.formatCount(p.playCount))
            }
            if (p.localCover != 0) {
                h.cover.setImageResource(p.localCover)
            } else {
                ImageLoader.load(h.cover, p.cover, 120)
            }
            h.itemView.setOnClickListener { onClick(p) }
        }
    }
}
