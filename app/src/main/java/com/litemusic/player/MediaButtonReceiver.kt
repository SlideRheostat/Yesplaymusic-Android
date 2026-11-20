package com.litemusic.player

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent

/**
 * API 19/20 没有 MediaSession，用系统媒体按键广播把耳机/蓝牙控制转发给播放服务。
 * API 21+ 由 MediaSession 接管，不再注册本接收器。
 */
class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        @Suppress("DEPRECATION")
        val event = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT) ?: return
        if (event.action != KeyEvent.ACTION_DOWN) return
        val action = when (event.keyCode) {
            KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerService.ACTION_RESUME
            KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerService.ACTION_PAUSE
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerService.ACTION_PLAY
            KeyEvent.KEYCODE_HEADSETHOOK -> PlayerService.ACTION_PLAY
            KeyEvent.KEYCODE_MEDIA_NEXT -> PlayerService.ACTION_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PlayerService.ACTION_PREV
            else -> return
        }
        try {
            context.startService(Intent(context, PlayerService::class.java).setAction(action))
        } catch (_: Exception) {
        }
    }
}
