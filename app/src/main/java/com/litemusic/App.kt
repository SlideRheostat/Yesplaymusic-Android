package com.litemusic

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        AudioCache.init(this)
        ImageLoader.init(this)
        HistoryStore.init(this)
        // API 19/20 通过 AppCompat 解析矢量资源（布局 src、菜单、drawable 引用）
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        applyTheme()
    }

    companion object {
        fun applyTheme() {
            AppCompatDelegate.setDefaultNightMode(
                when (Prefs.themeMode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
