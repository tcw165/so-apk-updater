package co.sodalabs.updaterengine.extension

import android.os.Build
import android.os.HandlerThread
import android.os.Looper

fun ensureMainThread() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        throw IllegalThreadStateException("Must run on MAIN thread")
    }
}

fun ensureNotMainThread() {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        throw IllegalThreadStateException("Must NOT run on MAIN thread")
    }
}

fun HandlerThread.quiteSafelyAndSmartly() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
        this.quitSafely()
    } else {
        this.quit()
    }
}