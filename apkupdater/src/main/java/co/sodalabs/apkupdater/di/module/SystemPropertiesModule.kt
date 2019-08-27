@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.ISystemProperties
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.settings.AndroidSystemProperties
import dagger.Binds
import dagger.Module

@Module
abstract class SystemPropertiesModule {

    @Binds
    @ApplicationScope
    abstract fun provideSystemProperties(
        systemProperties: AndroidSystemProperties
    ): ISystemProperties
}