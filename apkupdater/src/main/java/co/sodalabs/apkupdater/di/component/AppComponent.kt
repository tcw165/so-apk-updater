@file:Suppress("unused")

package co.sodalabs.apkupdater.di.component

import android.content.Context
import android.content.SharedPreferences
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.apkupdater.di.module.ApplicationContextModule
import co.sodalabs.apkupdater.di.module.SharedSettingsModule
import co.sodalabs.apkupdater.di.module.AppPreferenceModule
import co.sodalabs.apkupdater.di.module.ThreadSchedulersModule
import co.sodalabs.apkupdater.di.module.UpdaterModule
import co.sodalabs.apkupdater.feature.adminui.SettingsActivity
import co.sodalabs.apkupdater.feature.checker.CheckJobIntentService
import co.sodalabs.apkupdater.feature.heartbeat.HeartBeatService
import co.sodalabs.apkupdater.feature.settings.ISharedSettings
import co.sodalabs.updaterengine.IThreadSchedulers
import dagger.Component

@ApplicationScope
@Component(
    modules = [
        ApplicationContextModule::class,
        ThreadSchedulersModule::class,
        AppPreferenceModule::class,
        SharedSettingsModule::class,
        UpdaterModule::class
    ]
)
interface AppComponent {

    fun inject(app: UpdaterApp)
    fun inject(service: CheckJobIntentService)
    fun inject(service: HeartBeatService)
    fun inject(activity: SettingsActivity)

    fun provideApplicationContext(): Context
    fun provideSchedulers(): IThreadSchedulers
    fun provideSharedPreference(): SharedPreferences
    fun provideSettingsRepository(): ISharedSettings
    fun provideAppPreference(): IAppPreference
}