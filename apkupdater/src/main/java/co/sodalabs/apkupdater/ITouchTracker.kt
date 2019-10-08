package co.sodalabs.apkupdater

import android.graphics.Point
import android.view.MotionEvent
import io.reactivex.Observable

interface ITouchTracker {
    fun observeAnyTouches(): Observable<MotionEvent>
    fun observeUps(): Observable<Triple<Point, Int, Boolean>>
    fun trackEvent(ev: MotionEvent)
}