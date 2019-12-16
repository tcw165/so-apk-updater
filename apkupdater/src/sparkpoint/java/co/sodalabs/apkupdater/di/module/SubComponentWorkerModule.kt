@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.WorkerScope
import co.sodalabs.apkupdater.feature.homeCorrector.HomeActivityCorrectorWorker
import co.sodalabs.apkupdater.feature.homeCorrector.HomeActivityLauncherWorker
import co.sodalabs.apkupdater.feature.watchdog.ForegroundAppWatchdogWorker
import co.sodalabs.updaterengine.di.modules.WorkerInjectionModule
import co.sodalabs.updaterengine.feature.logPersistence.LogPersistenceWorker
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentWorkerModule {

    /**
     * The sub-component for [LogPersistenceWorker]
     */
    @WorkerScope
    @ContributesAndroidInjector(modules = [
        // Module for binding worker injection
        WorkerInjectionModule::class
    ])
    abstract fun contributeFollowupContentSendingWorkerInjector(): LogPersistenceWorker

    /**
     * The sub-component for [ForegroundAppWatchdogWorker]
     */
    @WorkerScope
    @ContributesAndroidInjector(modules = [
        // Module for binding worker injection
        WorkerInjectionModule::class
    ])
    abstract fun contributeForegroundAppWatchdogWorkerInjector(): ForegroundAppWatchdogWorker

    /**
     * The sub-component for [HomeActivityLauncherWorker]
     */
    @WorkerScope
    @ContributesAndroidInjector(modules = [
        // Module for binding worker injection
        WorkerInjectionModule::class
    ])
    abstract fun contributeHomeActivityLauncherWorkerInjector(): HomeActivityLauncherWorker

    /**
     * The sub-component for [HomeActivityCorrectorWorker]
     */
    @WorkerScope
    @ContributesAndroidInjector(modules = [
        // Module for binding worker injection
        WorkerInjectionModule::class
    ])
    abstract fun contributeHomeCorrectorWorkerInjector(): HomeActivityCorrectorWorker
}