@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ActivityScope
import co.sodalabs.apkupdater.feature.checker.CheckJobIntentService
import co.sodalabs.apkupdater.feature.heartbeat.HeartBeatJobIntentService
import co.sodalabs.updaterengine.feature.downloader.DownloadJobIntentService
import co.sodalabs.updaterengine.feature.installer.InstallerJobIntentService
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentServiceModule {

    /**
     * The sub-component of heartbeat Service
     */
    @ActivityScope
    @ContributesAndroidInjector
    abstract fun contributeHeartbeatServiceInjector(): HeartBeatJobIntentService

    /**
     * The sub-component of check Service
     */
    @ActivityScope
    @ContributesAndroidInjector
    abstract fun contributeCheckerServiceInjector(): CheckJobIntentService

    /**
     * The sub-component of download Service
     */
    @ActivityScope
    @ContributesAndroidInjector
    abstract fun contributeDownloadServiceInjector(): DownloadJobIntentService

    /**
     * The sub-component of install Service
     */
    @ActivityScope
    @ContributesAndroidInjector
    abstract fun contributeInstallServiceInjector(): InstallerJobIntentService
}