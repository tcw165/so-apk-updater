package co.sodalabs.apkupdater

import androidx.multidex.MultiDexApplication
import co.sodalabs.apkupdater.di.component.AppComponent
import co.sodalabs.apkupdater.di.component.DaggerAppComponent
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.ConfigHelper
import co.sodalabs.apkupdater.utils.CrashlyticsTree
import co.sodalabs.updaterengine.ApkUpdater
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import io.fabric.sdk.android.Fabric
import timber.log.Timber
import javax.inject.Inject

class UpdaterApp : MultiDexApplication() {

    companion object {

        lateinit var appComponent: AppComponent
    }

    @Inject
    lateinit var appUpdatesChecker: AppUpdatesChecker
    @Inject
    lateinit var appUpdatesDownloader: AppUpdatesDownloader
    @Inject
    lateinit var appUpdatesInstaller: AppUpdatesInstaller
    @Inject
    lateinit var heartBeater: AppUpdaterHeartBeater

    override fun onCreate() {
        super.onCreate()
        initLogging()

        Timber.d("App Version Name: ${BuildConfig.VERSION_NAME}")
        Timber.d("App Version Code: ${BuildConfig.VERSION_CODE}")

        initCrashReporting()
        initDI()

        ApkUpdater.install(
            app = this,
            config = ConfigHelper.generateDefault(this),
            appUpdatesChecker = appUpdatesChecker,
            appUpdatesDownloader = appUpdatesDownloader,
            appUpdatesInstaller = appUpdatesInstaller,
            engineHeartBeater = heartBeater,
            // TODO: Deprecate this
            schedulers = schedulers)
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

    // Application Singletons /////////////////////////////////////////////////

    private val schedulers = AppThreadSchedulers()

    private fun initDI() {
        // Application singleton(s)
        appComponent = DaggerAppComponent.builder()
            .applicationContextModule(ApplicationContextModule(this))
            .threadSchedulersModule(ThreadSchedulersModule(schedulers))
            .updaterModule(UpdaterModule(this, schedulers))
            .build()
        appComponent.inject(this)
    }
}