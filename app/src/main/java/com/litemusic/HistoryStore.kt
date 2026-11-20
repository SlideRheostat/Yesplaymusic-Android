package com.litemusic

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 本地历史记录：最近播放的 50 首不重复歌曲，最新在前，超出后淘汰最旧一首。
 * 作为私人歌单展示在歌单列表最后，并支持本地搜索。
 */
object HistoryStore {

    const val MAX = 50
    const val PLAYLIST_ID = -1001L

    private const val PREFS = "history"
    private const val KEY = "songs"

    private val songs = ArrayList<Song>()
    private var prefs: SharedPreferences? = null
    private var loaded = false

    @Synchronized
    fun init(ctx: Context) {
        if (loaded) return
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs?.getString(KEY, "") ?: ""
        if (raw.isNotEmpty()) {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val s = fromJson(o)
                    if (s.id > 0 && songs.none { it.id == s.id }) songs.add(s)
                }
            } catch (_: Exception) {
                songs.clear()
            }
        }
        loaded = true
    }

    /** 记录一次播放：已存在则移到最前，满 50 首后淘汰最后（最旧）一首 */
    @Synchronized
    fun record(song: Song) {
        if (song.id <= 0) return
        songs.removeAll { it.id == song.id }
        songs.add(0, song)
        while (songs.size > MAX) songs.removeAt(songs.size - 1)
        persist()
    }

    @Synchronized
    fun songs(): List<Song> = songs.toList()

    /** 历史记录歌单（放在歌单列表最后，使用本地封面） */
    fun playlist(ctx: Context): Playlist = Playlist(
        id = PLAYLIST_ID,
        name = ctx.getString(R.string.history_playlist),
        cover = null,
        playCount = 0,
        trackCount = songs.size,
        localCover = R.drawable.cover_history,
        history = true,
    )

    private fun persist() {
        val arr = JSONArray()
        for (s in songs) arr.put(toJson(s))
        prefs?.edit()?.putString(KEY, arr.toString())?.apply()
    }

    private fun toJson(s: Song): JSONObject {
        val o = JSONObject()
            .put("id", s.id)
            .put("name", s.name)
            .put("artists", s.artists)
            .put("artistId", s.artistId)
            .put("album", s.album)
            .put("albumId", s.albumId)
            .put("duration", s.duration)
            .put("cover", s.cover ?: "")
            .put("fee", s.fee)
            .put("offline", s.offline)
        val ar = JSONArray()
        for ((id, name) in s.artistList) {
            ar.put(JSONArray().put(id).put(name))
        }
        o.put("artistList", ar)
        return o
    }

    private fun fromJson(o: JSONObject): Song {
        val ar = ArrayList<Pair<Long, String>>()
        val artistsArr = o.optJSONArray("artistList")
        if (artistsArr != null) {
            for (i in 0 until artistsArr.length()) {
                val pair = artistsArr.optJSONArray(i) ?: continue
                val id = pair.optLong(0)
                val name = pair.optString(1)
                if (id > 0 && name.isNotEmpty()) ar.add(id to name)
            }
        }
        return Song(
            id = o.optLong("id"),
            name = o.optString("name"),
            artists = o.optString("artists"),
            artistId = o.optLong("artistId"),
            artistList = ar,
            album = o.optString("album"),
            albumId = o.optLong("albumId"),
            duration = o.optLong("duration"),
            cover = o.optString("cover").ifEmpty { null },
            fee = o.optInt("fee"),
            offline = o.optBoolean("offline"),
        )
    }
}
