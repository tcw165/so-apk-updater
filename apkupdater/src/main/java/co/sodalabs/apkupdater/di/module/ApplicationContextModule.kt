@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.UpdaterApp
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import dagger.Module
import dagger.Provides

@Module
class ApplicationContextModule(
    private val application: UpdaterApp
) {

    @Provides
    @ApplicationScope
    fun getApplicationContext(): Context = application
}