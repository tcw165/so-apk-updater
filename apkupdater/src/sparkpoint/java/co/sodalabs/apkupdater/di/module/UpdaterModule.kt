@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.checker.SparkPointUpdatesChecker
import co.sodalabs.apkupdater.feature.heartbeat.SparkPointHeartBeater
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.feature.downloader.DefaultUpdatesDownloader
import co.sodalabs.updaterengine.feature.installer.DefaultAppUpdatesInstaller
import dagger.Binds
import dagger.Module

@Module
abstract class UpdaterModule {

    @Binds
    @ApplicationScope
    abstract fun castApplicationToContext(
        app: UpdaterApp
    ): Context

    @Binds
    @ApplicationScope
    abstract fun provideHeartBeater(
        heartBeater: SparkPointHeartBeater
    ): AppUpdaterHeartBeater

    @Binds
    @ApplicationScope
    abstract fun provideAppUpdatesChecker(
        checker: SparkPointUpdatesChecker
    ): AppUpdatesChecker

    @Binds
    @ApplicationScope
    abstract fun provideAppUpdatesDownloader(
        downloader: DefaultUpdatesDownloader
    ): AppUpdatesDownloader

    @Binds
    @ApplicationScope
    abstract fun provideAppUpdatesInstaller(
        installer: DefaultAppUpdatesInstaller
    ): AppUpdatesInstaller
}