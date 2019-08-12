@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.ISystemProperties
import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.apkupdater.feature.settings.AndroidSystemProperties
import dagger.Module
import dagger.Provides

@Module
class SystemPropertiesModule(
    private val systemProperties: AndroidSystemProperties
) {

    @Provides
    @ApplicationScope
    fun provideSystemProperties(): ISystemProperties = systemProperties
}