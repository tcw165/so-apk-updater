package co.sodalabs.apkupdater

import android.support.multidex.MultiDexApplication
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.ConfigHelper
import co.sodalabs.apkupdater.utils.CrashlyticsTree
import co.sodalabs.updaterengine.ApkUpdater
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import io.fabric.sdk.android.Fabric
import timber.log.Timber

class UpdaterApp : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        initLogging()

        Timber.d("App Version Name: ${BuildConfig.VERSION_NAME}")
        Timber.d("App Version Code: ${BuildConfig.VERSION_CODE}")

        initCrashReporting()
        ApkUpdater.install(this, ConfigHelper.getDefault(this))
    }

    private fun initLogging() {
        if (BuildUtils.isDebug() || BuildUtils.isStaging()) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun initCrashReporting() {
        val crashlyticsCore = CrashlyticsCore.Builder()
            .disabled(BuildUtils.isDebug())
            .build()

        val crashlytics = Crashlytics.Builder()
            .core(crashlyticsCore)
            .build()

        val builder = Fabric.Builder(this)
            .kits(crashlytics)

        Fabric.with(builder.build())

        if (BuildUtils.isStaging() || BuildUtils.isRelease()) {
            Timber.plant(CrashlyticsTree())
        }
    }
}
