package co.sodalabs.apkupdater

import android.content.Context
import android.graphics.Point
import android.util.SparseArray
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.jakewharton.rxrelay2.PublishRelay
import io.reactivex.Observable
import javax.inject.Inject

typealias PointUpListener = (touchPoint: Point, pointerId: Int, isTap: Boolean) -> Unit

class AndroidTouchTracker @Inject constructor(
    private val context: Context
) : ITouchTracker {

    // TODO: Can we not use Relay?
    private val touchRelay = PublishRelay.create<MotionEvent>().toSerialized()

    override fun observeAnyTouches(): Observable<MotionEvent> = touchRelay

    private var pointDownSet = SparseArray<Pair<Point, Boolean>>()
    private val upListeners = mutableListOf<PointUpListener>()

    override fun observeUps(): Observable<Triple<Point, Int, Boolean>> {
        return Observable.create { emitter ->
            val listener = object : PointUpListener {
                override fun invoke(touchPoint: Point, pointerId: Int, isTap: Boolean) {
                    emitter.onNext(Triple(touchPoint, pointerId, isTap))
                }
            }
            upListeners.add(listener)

            emitter.setCancellable { upListeners.remove(listener) }
        }
    }

    override fun trackEvent(ev: MotionEvent) {
        // Relay the touch.
        touchRelay.accept(ev)

        val index = ev.actionIndex
        val action = ev.actionMasked
        val pointerId = ev.getPointerId(index)
        val touchPoint = ev.toRawPoint(pointerId)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                pointDownSet.put(pointerId, touchPoint to false)
            }
            MotionEvent.ACTION_MOVE -> {
                val (down, didMove) = pointDownSet.get(pointerId, touchPoint to false)
                if (!didMove && !context.withinTouchSlop(down, touchPoint)) {
                    pointDownSet.put(pointerId, down to true)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val (_, didMove) = pointDownSet.get(pointerId, touchPoint to false)
                pointDownSet.remove(pointerId)

                upListeners.forEach {
                    it.invoke(touchPoint, pointerId, !didMove)
                }
            }
        }
    }

    private fun MotionEvent.toRawPoint(pointerId: Int): Point {
        return Point(
            getX(findPointerIndex(pointerId)).toInt(),
            getY(findPointerIndex(pointerId)).toInt())
    }

    private fun Context.withinTouchSlop(down: Point, up: Point): Boolean {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        return Math.abs(up.x - down.x) < slop && Math.abs(up.y - down.y) < slop
    }
}