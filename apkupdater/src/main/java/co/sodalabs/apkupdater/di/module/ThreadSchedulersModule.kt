@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.updaterengine.IThreadSchedulers
import dagger.Module
import dagger.Provides

@Module
class ThreadSchedulersModule(
    private val schedulers: IThreadSchedulers
) {

    @Provides
    @ApplicationScope
    fun getSchedulers(): IThreadSchedulers = schedulers
}