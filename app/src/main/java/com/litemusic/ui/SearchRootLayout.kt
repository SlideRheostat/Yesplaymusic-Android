package com.litemusic.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout

/**
 * 搜索页根布局：在搜索框之外抬起手指时通知外部收起键盘。
 * 只观察事件、不拦截，保证列表点击与滚动不受影响。
 */
class SearchRootLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var ignoreView: View? = null
    var onOutsideTouchUp: (() -> Unit)? = null

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_UP) {
            val v = ignoreView
            if (v != null && v.width > 0 && v.height > 0 && !contains(v, ev)) {
                onOutsideTouchUp?.invoke()
            }
        }
        return handled
    }

    private fun contains(v: View, ev: MotionEvent): Boolean {
        val loc = IntArray(2)
        v.getLocationOnScreen(loc)
        return ev.rawX >= loc[0] && ev.rawX <= loc[0] + v.width &&
            ev.rawY >= loc[1] && ev.rawY <= loc[1] + v.height
    }
}
