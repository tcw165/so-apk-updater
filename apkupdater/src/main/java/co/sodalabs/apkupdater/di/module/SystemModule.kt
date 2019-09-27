@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidSystemProperties
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
    abstract fun provideRebootHelper(
        helper: AndroidRebootHelper
    ): IRebootHelper
}