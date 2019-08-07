package co.sodalabs.apkupdater.utils

import android.util.Log
import com.bugsnag.android.Bugsnag
import timber.log.Timber

class BugsnagTree : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val prioritiesToBreadcrumb = setOf(Log.VERBOSE, Log.DEBUG, Log.INFO)
        if (priority in prioritiesToBreadcrumb) {
            Bugsnag.leaveBreadcrumb(message)
        }

        t?.let { Bugsnag.notify(it) }
    }
}
