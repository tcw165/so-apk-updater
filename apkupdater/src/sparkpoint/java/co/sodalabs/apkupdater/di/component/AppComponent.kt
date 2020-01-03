@file:Suppress("unused")

package co.sodalabs.apkupdater.di.component

import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.module.AppPreferenceModule
import co.sodalabs.apkupdater.di.module.ComponentLauncherModule
import co.sodalabs.apkupdater.di.module.LoggingModule
import co.sodalabs.apkupdater.di.module.MapperModule
import co.sodalabs.apkupdater.di.module.NetworkModule
import co.sodalabs.apkupdater.di.module.SubComponentActivityModule
import co.sodalabs.apkupdater.di.module.SubComponentProviderModule
import co.sodalabs.apkupdater.di.module.SubComponentReceiverModule
import co.sodalabs.apkupdater.di.module.SubComponentServiceModule
import co.sodalabs.apkupdater.di.module.SubComponentWorkerModule
import co.sodalabs.apkupdater.di.module.SystemModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.TrackersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.di.module.UtilsModule
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.ISystemProperties
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.di.modules.WorkMiscModule
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
    SystemModule::class,
    NetworkModule::class,
    TrackersModule::class,
    UtilsModule::class,
    MapperModule::class,
    UpdaterModule::class,
    LoggingModule::class,
    WorkMiscModule::class,
    ComponentLauncherModule::class,
    // Modules for constructing sub-components
    SubComponentActivityModule::class,
    SubComponentServiceModule::class,
    SubComponentReceiverModule::class,
    SubComponentWorkerModule::class,
    SubComponentProviderModule::class
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

        @BindsInstance
        fun setPackageManager(manager: PackageManager): Builder

        fun build(): AppComponent
    }

    fun provideApplicationContext(): Context
    fun provideSchedulers(): IThreadSchedulers
    fun provideSharedPreference(): SharedPreferences
    fun provideSettingsRepository(): ISharedSettings
    fun provideSystemProperties(): ISystemProperties
    fun provideAppPreference(): IAppPreference
}