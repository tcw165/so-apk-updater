package co.sodalabs.apkupdater.utils

import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.PreferenceProps
import timber.log.Timber

class SessionLoggingTree(
    private val prefs: IAppPreference
) : Timber.DebugTree() {

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val sessionId = prefs.getString(PreferenceProps.SESSION_ID, "-")
        val messageWithSessionId = "[$sessionId] $message"
        val throwableWithSessionId = Throwable("[$sessionId] ${t?.message}")
        super.log(priority, tag, messageWithSessionId, throwableWithSessionId)
    }
}