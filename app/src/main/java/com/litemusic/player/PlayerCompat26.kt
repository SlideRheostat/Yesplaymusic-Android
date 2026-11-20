package com.litemusic.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi

/**
 * API 26+ 专属逻辑（通知渠道、渠道化 Builder、startForegroundService）。
 * API 19/20/21-25 不加载本类，避免旧 Dalvik 校验到尚不存在的平台类型。
 */
@RequiresApi(26)
object PlayerCompat26 {

    fun startForegroundService(ctx: Context, intent: Intent) {
        ctx.startForegroundService(intent)
    }

    fun createPlaybackChannel(svc: Service, nm: NotificationManager, name: String) {
        val ch = NotificationChannel("playback", name, NotificationManager.IMPORTANCE_LOW)
        ch.setShowBadge(false)
        nm.createNotificationChannel(ch)
    }

    fun baseBuilder(svc: Service): Notification.Builder = Notification.Builder(svc, "playback")
}
