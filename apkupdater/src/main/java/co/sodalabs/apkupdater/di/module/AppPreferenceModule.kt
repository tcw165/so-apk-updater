@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AppSharedPreference
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import dagger.Binds
import dagger.Module

@Module
abstract class AppPreferenceModule {

    @Binds
    @ApplicationScope
    abstract fun provideAppPreference(appPreference: AppSharedPreference): IAppPreference
}