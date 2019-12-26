@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.remoteConfig.ITimezoneMapper
import co.sodalabs.apkupdater.feature.remoteConfig.SparkpointTimezoneMapper
import dagger.Binds
import dagger.Module

@Module
abstract class MapperModule {

    @Binds
    @ApplicationScope
    abstract fun provideTimezoneMapper(mapper: SparkpointTimezoneMapper): ITimezoneMapper
}