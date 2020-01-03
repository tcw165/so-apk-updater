@file:Suppress("unused")

package co.sodalabs.updaterengine.di.modules

import co.sodalabs.updaterengine.AndroidWorkObserver
import co.sodalabs.updaterengine.IWorkObserver
import dagger.Binds
import dagger.Module

@Module
abstract class WorkMiscModule {

    @Binds
    abstract fun provideWorkObserver(observer: AndroidWorkObserver): IWorkObserver
}