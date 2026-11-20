package com.litemusic.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.litemusic.HistoryStore
import com.litemusic.R
import com.litemusic.Song
import com.litemusic.player.PlayerService

/** 历史记录：本地保存的最近 50 首不重复歌曲 */
class HistoryActivity : AppCompatActivity() {

    companion object {
        fun launch(ctx: Context): Intent = Intent(ctx, HistoryActivity::class.java)
    }

    private lateinit var recycler: RecyclerView
    private lateinit var emptyTip: TextView
    private lateinit var playAll: MaterialButton
    private lateinit var mini: MiniPlayerController

    private var songs: List<Song> = emptyList()
    private var firstResume = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        recycler = findViewById(R.id.recycler)
        emptyTip = findViewById(R.id.empty_tip)
        playAll = findViewById(R.id.btn_play_all)
        recycler.layoutManager = LinearLayoutManager(this)

        findViewById<ImageButton>(R.id.btn_back).setOnClickListener { finish() }
        playAll.setOnClickListener {
            if (songs.isEmpty()) {
                Toast.makeText(this, R.string.history_empty, Toast.LENGTH_SHORT).show()
            } else {
                PlayerService.play(this, songs, 0)
            }
        }

        mini = MiniPlayerController(this, findViewById(android.R.id.content))
        reload()
    }

    override fun onStart() {
        super.onStart()
        mini.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (firstResume) firstResume = false else reload()
    }

    override fun onStop() {
        mini.onStop()
        super.onStop()
    }

    private fun reload() {
        songs = HistoryStore.songs()
        emptyTip.visibility = if (songs.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
        playAll.isEnabled = songs.isNotEmpty()
        recycler.adapter = SongAdapter(songs, showLike = false) { index ->
            PlayerService.play(this, songs, index)
        }
    }
}
