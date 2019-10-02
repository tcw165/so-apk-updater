@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidPackageVersionProvider
import co.sodalabs.apkupdater.AndroidSharedSettings
import co.sodalabs.apkupdater.AndroidSystemProperties
import co.sodalabs.apkupdater.IPackageVersionProvider
import co.sodalabs.apkupdater.ISharedSettings
import co.sodalabs.apkupdater.ISystemProperties
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.AndroidRebootHelper
import co.sodalabs.updaterengine.IRebootHelper
import dagger.Binds
import dagger.Module

@Module
abstract class SystemModule {

    @Binds
    @ApplicationScope
    abstract fun provideSystemProperties(
        systemProperties: AndroidSystemProperties
    ): ISystemProperties

    @Binds
    @ApplicationScope
    abstract fun provideSharedSettingsRepository(
        sharedSettings: AndroidSharedSettings
    ): ISharedSettings

    @Binds
    @ApplicationScope
    abstract fun providePackageVersionProvider(
        provider: AndroidPackageVersionProvider
    ): IPackageVersionProvider

    @Binds
    @ApplicationScope
    abstract fun provideRebootHelper(
        helper: AndroidRebootHelper
    ): IRebootHelper
}