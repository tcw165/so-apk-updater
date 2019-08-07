//package co.sodalabs.apkupdater.utils
//
//import android.util.Log
//import com.crashlytics.android.Crashlytics
//import timber.log.Timber
//
//private const val CRASHLYTICS_KEY_PRIORITY = "priority"
//private const val CRASHLYTICS_KEY_TAG = "tag"
//private const val CRASHLYTICS_KEY_MESSAGE = "message"
//
//class CrashlyticsTree : Timber.Tree() {
//
//    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
//        val prioritiesToSkip = setOf(Log.VERBOSE, Log.DEBUG, Log.INFO)
//        if (priority in prioritiesToSkip) {
//            return
//        }
//
//        Crashlytics.setInt(CRASHLYTICS_KEY_PRIORITY, priority)
//        Crashlytics.setString(CRASHLYTICS_KEY_TAG, tag)
//        Crashlytics.setString(CRASHLYTICS_KEY_MESSAGE, message)
//
//        if (t == null) {
//            Crashlytics.logException(Exception(message))
//        } else {
//            Crashlytics.logException(t)
//        }
//    }
//}
