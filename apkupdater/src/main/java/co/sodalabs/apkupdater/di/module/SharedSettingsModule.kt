@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.apkupdater.feature.settings.AndroidSharedSettings
import co.sodalabs.apkupdater.ISharedSettings
import dagger.Module
import dagger.Provides

@Module
class SharedSettingsModule(
    private val sharedSettings: AndroidSharedSettings
) {

    @Provides
    @ApplicationScope
    fun provideSettingsRepository(): ISharedSettings = sharedSettings
}