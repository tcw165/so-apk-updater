package co.sodalabs.apkupdater

import android.graphics.Point
import android.view.MotionEvent
import io.reactivex.Observable

interface ITouchTracker {
    fun observeAnyTouches(): Observable<MotionEvent>
    fun observeUps(): Observable<Triple<Point, Int, Boolean>>
    // TODO: What about the IME? We should extend the timeout when using IME too!
    fun trackEvent(ev: MotionEvent)
}