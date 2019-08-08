package co.sodalabs.apkupdater.utils

import android.util.Log
import com.bugsnag.android.Bugsnag
import timber.log.Timber

class BugsnagTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Bugsnag.leaveBreadcrumb(message)

        val prioritiesToSkip = setOf(Log.VERBOSE, Log.DEBUG, Log.INFO)
        if (priority in prioritiesToSkip) {
            return
        }

        // Any message or error here is either warning or error.
        t?.let {
            Bugsnag.notify(it)
        } ?: kotlin.run {
            Bugsnag.notify(RuntimeException(message))
        }
    }
}