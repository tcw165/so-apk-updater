@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.remoteConfig.IRemoteConfigSyncLauncher
import co.sodalabs.apkupdater.feature.remoteConfig.RemoteConfigSyncLauncher
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogLauncher
import co.sodalabs.apkupdater.feature.watchdog.IForegroundAppWatchdogLauncher
import dagger.Binds
import dagger.Module

@Module
abstract class ComponentLauncherModule {

    @Binds
    @ApplicationScope
    abstract fun provideForegroundAppWatchdogLauncher(launcher: ForegroundAppWatchdogLauncher): IForegroundAppWatchdogLauncher

    @Binds
    @ApplicationScope
    abstract fun provideRemoteConfigWorkLauncher(launcher: RemoteConfigSyncLauncher): IRemoteConfigSyncLauncher
}