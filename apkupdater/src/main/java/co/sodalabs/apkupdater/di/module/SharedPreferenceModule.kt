@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AppSharedPreference
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.di.ApplicationScope
import dagger.Module
import dagger.Provides

@Module
class SharedPreferenceModule(
    private val preferences: AppSharedPreference
) {

    @Provides
    @ApplicationScope
    fun getApplicationPreference(): IAppPreference = preferences
}