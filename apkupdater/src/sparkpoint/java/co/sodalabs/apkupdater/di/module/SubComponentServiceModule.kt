@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ServiceScope
import co.sodalabs.apkupdater.feature.checker.CheckJobIntentService
import co.sodalabs.apkupdater.feature.heartbeat.HeartBeatJobIntentService
import co.sodalabs.updaterengine.UpdaterService
import co.sodalabs.updaterengine.feature.downloader.DownloadJobIntentService
import co.sodalabs.updaterengine.feature.installer.InstallerJobIntentService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentServiceModule {

    /**
     * The sub-component of updater Service (core component)
     */
    @ServiceScope
    @ContributesAndroidInjector
    abstract fun contributeUpdaterServiceInjector(): UpdaterService

    /**
     * The sub-component of heartbeat Service
     */
    @ServiceScope
    @ContributesAndroidInjector
    abstract fun contributeHeartbeatServiceInjector(): HeartBeatJobIntentService

    /**
     * The sub-component of check Service
     */
    @ServiceScope
    @ContributesAndroidInjector
    abstract fun contributeCheckerServiceInjector(): CheckJobIntentService

    /**
     * The sub-component of download Service
     */
    @ServiceScope
    @ContributesAndroidInjector
    abstract fun contributeDownloadServiceInjector(): DownloadJobIntentService

    /**
     * The sub-component of install Service
     */
    @ServiceScope
    @ContributesAndroidInjector
    abstract fun contributeInstallServiceInjector(): InstallerJobIntentService
}