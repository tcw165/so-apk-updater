@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AppThreadSchedulers
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.updaterengine.IThreadSchedulers
import dagger.Module
import dagger.Provides

@Module
class ThreadSchedulersModule {

    @Provides
    @ApplicationScope
    fun getSchedulers(): IThreadSchedulers = AppThreadSchedulers()
}