@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.ISharedSettings
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.settings.AndroidSharedSettings
import dagger.Binds
import dagger.Module

@Module
abstract class SharedSettingsModule {

    @Binds
    @ApplicationScope
    abstract fun provideSettingsRepository(sharedSettings: AndroidSharedSettings): ISharedSettings
}