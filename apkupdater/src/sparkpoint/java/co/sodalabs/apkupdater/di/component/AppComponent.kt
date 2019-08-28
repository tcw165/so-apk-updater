@file:Suppress("unused")

package co.sodalabs.apkupdater.di.component

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.ISharedSettings
import co.sodalabs.apkupdater.ISystemProperties
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.module.AppPreferenceModule
import co.sodalabs.apkupdater.di.module.NetworkModule
import co.sodalabs.apkupdater.di.module.SharedSettingsModule
import co.sodalabs.apkupdater.di.module.SubComponentActivityModule
import co.sodalabs.apkupdater.di.module.SubComponentServiceModule
import co.sodalabs.apkupdater.di.module.SystemPropertiesModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.IThreadSchedulers
import dagger.BindsInstance
import dagger.Component
import dagger.android.AndroidInjector
import dagger.android.support.AndroidSupportInjectionModule

@ApplicationScope
@Component(modules = [
    // App's direct children modules
    AndroidSupportInjectionModule::class,
    // ApplicationContextModule::class,
    ThreadSchedulersModule::class,
    AppPreferenceModule::class,
    SharedSettingsModule::class,
    SystemPropertiesModule::class,
    NetworkModule::class,
    UpdaterModule::class,
    // Modules for constructing sub-components
    SubComponentActivityModule::class,
    SubComponentServiceModule::class
])
interface AppComponent : AndroidInjector<UpdaterApp> {

    @Component.Builder
    interface Builder {

        @BindsInstance
        fun setApplication(application: UpdaterApp): Builder

        @BindsInstance
        fun setAppPreference(preference: SharedPreferences): Builder

        @BindsInstance
        fun setContentResolver(resolver: ContentResolver): Builder

        fun build(): AppComponent
    }

    fun provideApplicationContext(): Context
    fun provideSchedulers(): IThreadSchedulers
    fun provideSharedPreference(): SharedPreferences
    fun provideSettingsRepository(): ISharedSettings
    fun provideSystemProperties(): ISystemProperties
    fun provideAppPreference(): IAppPreference
}