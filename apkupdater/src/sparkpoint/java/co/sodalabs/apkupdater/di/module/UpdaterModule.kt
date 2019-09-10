@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.AndroidUpdaterConfig
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.checker.SparkPointUpdatesChecker
import co.sodalabs.apkupdater.feature.heartbeat.SparkPointHeartBeater
import co.sodalabs.updaterengine.UpdaterConfig
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdatesChecker
import co.sodalabs.updaterengine.UpdatesDownloader
import co.sodalabs.updaterengine.UpdatesInstaller
import co.sodalabs.updaterengine.feature.downloader.DefaultUpdatesDownloader
import co.sodalabs.updaterengine.feature.installer.DefaultUpdatesInstaller
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
    ): UpdaterHeartBeater

    @Binds
    @ApplicationScope
    abstract fun provideUpdatesChecker(
        checker: SparkPointUpdatesChecker
    ): UpdatesChecker

    @Binds
    @ApplicationScope
    abstract fun provideUpdatesDownloader(
        downloader: DefaultUpdatesDownloader
    ): UpdatesDownloader

    @Binds
    @ApplicationScope
    abstract fun provideUpdatesInstaller(
        installer: DefaultUpdatesInstaller
    ): UpdatesInstaller

    @Binds
    @ApplicationScope
    abstract fun provideUpdaterConfig(
        config: AndroidUpdaterConfig
    ): UpdaterConfig
}