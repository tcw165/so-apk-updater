@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.homeCorrector.HomeActivityCorrectorLauncher
import co.sodalabs.apkupdater.feature.homeCorrector.IHomeCorrectorLauncher
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
    abstract fun provideHomeCorrectorLauncher(launcher: HomeActivityCorrectorLauncher): IHomeCorrectorLauncher
}