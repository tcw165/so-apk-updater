package co.sodalabs.apkupdater.utils

import android.util.Log
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Client
import timber.log.Timber

class BugsnagTree(
    private val bugTracker: Client
) : Timber.Tree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Bugsnag.leaveBreadcrumb(message)

        val prioritiesToSkip = setOf(Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN)
        if (priority in prioritiesToSkip) {
            return
        }

        // Any message or error here is either warning or error.
        t?.let {
            bugTracker.notify(it)
        } ?: kotlin.run {
            bugTracker.notify(RuntimeException(message))
        }
    }
}