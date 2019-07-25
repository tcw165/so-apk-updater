@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.SharedPreferences
import co.sodalabs.apkupdater.AppSharedPreference
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.di.ApplicationScope
import dagger.Module
import dagger.Provides

@Module
class SharedPreferenceModule(
    private val appPreference: AppSharedPreference
) {

    @Provides
    @ApplicationScope
    fun provideSharedPreference(): SharedPreferences = appPreference.preferences

    @Provides
    @ApplicationScope
    fun provideApplicationPreference(): IAppPreference = appPreference
}