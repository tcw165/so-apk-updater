package co.sodalabs.apkupdater.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import co.sodalabs.updaterengine.extension.ensureMainThread

typealias TouchEventBridger = (ev: MotionEvent) -> Unit

/**
 * Bypass the touch event to the listener, [TouchEventBridger].
 */
open class TouchBridgeFrameLayout : FrameLayout {

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Volatile
    private var touchBridger: TouchEventBridger? = null

    fun setTouchBridger(
        bridger: TouchEventBridger?
    ) {
        ensureMainThread()
        touchBridger = bridger
    }

    override fun dispatchTouchEvent(
        ev: MotionEvent
    ): Boolean {
        touchBridger?.invoke(ev)
        return super.dispatchTouchEvent(ev)
    }
}