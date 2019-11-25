@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.logpersistence.LogFileProvider
import co.sodalabs.apkupdater.feature.logpersistence.LogPersistenceConfig
import co.sodalabs.apkupdater.feature.logpersistence.LogSender
import co.sodalabs.updaterengine.feature.logPersistence.ILogFileProvider
import co.sodalabs.updaterengine.feature.logPersistence.ILogPersistenceConfig
import co.sodalabs.updaterengine.feature.logPersistence.ILogSender
import co.sodalabs.updaterengine.feature.logPersistence.ILogsPersistenceScheduler
import co.sodalabs.updaterengine.feature.logPersistence.LogsPersistenceScheduler
import dagger.Binds
import dagger.Module

@Module
abstract class LoggingModule {

    @Binds
    @ApplicationScope
    abstract fun provideLogsPersistence(scheduler: LogsPersistenceScheduler): ILogsPersistenceScheduler

    @Binds
    @ApplicationScope
    abstract fun provideLogSender(scheduler: LogSender): ILogSender

    @Binds
    @ApplicationScope
    abstract fun provideLogPersistenceConfig(config: LogPersistenceConfig): ILogPersistenceConfig

    @Binds
    @ApplicationScope
    abstract fun provideLogFileProvider(provider: LogFileProvider): ILogFileProvider
}