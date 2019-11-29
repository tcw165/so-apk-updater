@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.WorkerScope
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
}