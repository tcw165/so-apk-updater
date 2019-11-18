@file:Suppress("unused")

package co.sodalabs.privilegedinstaller

import android.os.StrictMode
import androidx.multidex.MultiDexApplication
import co.sodalabs.privilegedinstaller.utils.BugsnagTree
import co.sodalabs.privilegedinstaller.utils.BuildUtils
import com.bugsnag.android.Bugsnag
import com.bugsnag.android.Configuration
import timber.log.Timber

class PrivilegedInstallerApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()

        initLogging()
        initCrashReporting()
        initStrictMode()
    }

    private fun initLogging() {
        if (BuildUtils.isDebug() || BuildUtils.isPreRelease()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initCrashReporting() {
        val config = Configuration(BuildConfig.BUGSNAG_API_KEY).apply {
            // Only send report for staging and release
            notifyReleaseStages = arrayOf(BuildUtils.TYPE_PRE_RELEASE, BuildUtils.TYPE_RELEASE)
            releaseStage = BuildConfig.BUILD_TYPE
        }
        Bugsnag.init(this, config)
        Timber.plant(BugsnagTree())
    }

    private fun initStrictMode() {
        if (BuildUtils.isDebug() || BuildUtils.isPreRelease()) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder()
                // Note: You'll see a lot of warning by enabling disk reads cause
                // the shared preference and system settings all cache the database
                // in MAIN thread in their initialization.
                // Basically, all the system components related to ContentProvider
                // share this common issue.
                // .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork() // or .detectAll() for all detectable problems
                // Note: The log uses LogCat directly and we have no control such as
                // logging to the crashlytics service.
                .penaltyLog()
                .penaltyFlashScreen()
                .build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder()
                .detectLeakedSqlLiteObjects()
                .detectLeakedClosableObjects()
                .penaltyLog()
                .penaltyDeath()
                .build())
        }
    }
}