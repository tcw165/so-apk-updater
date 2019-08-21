@file:Suppress("unused")

package co.sodalabs.privilegedinstaller

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
    }

    private fun initLogging() {
        if (BuildUtils.isDebug() || BuildUtils.isStaging()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initCrashReporting() {
        val config = Configuration(BuildConfig.BUGSNAG_API_KEY).apply {
            // Only send report for staging and release
            notifyReleaseStages = arrayOf(BuildUtils.TYPE_STAGING, BuildUtils.TYPE_RELEASE)
            releaseStage = BuildConfig.BUILD_TYPE
        }
        Bugsnag.init(this, config)
        Timber.plant(BugsnagTree())
    }
}