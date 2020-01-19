@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidPackageVersionProvider
import co.sodalabs.apkupdater.AndroidSystemProperties
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.AndroidRebootHelper
import co.sodalabs.updaterengine.AndroidSharedSettings
import co.sodalabs.updaterengine.IPackageVersionProvider
import co.sodalabs.updaterengine.IRebootHelper
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.utils.AndroidPreRebootCleaner
import co.sodalabs.updaterengine.utils.IPreRebootCleaner
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

    @Binds
    @ApplicationScope
    abstract fun providePreRebootCleaner(
        helper: AndroidPreRebootCleaner
    ): IPreRebootCleaner
}