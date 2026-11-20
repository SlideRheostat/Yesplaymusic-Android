package com.litemusic.player

import android.app.Notification
import android.app.Service
import android.content.pm.ServiceInfo
import androidx.annotation.RequiresApi

/**
 * API 29+ 专属逻辑（带 foregroundServiceType 的 startForeground）。
 * 隔离到独立类，保证 API 19 设备不会校验到 29 才有的方法签名。
 */
@RequiresApi(29)
object PlayerCompat29 {

    fun startForegroundMedia(svc: Service, id: Int, notification: Notification) {
        svc.startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
    }
}
