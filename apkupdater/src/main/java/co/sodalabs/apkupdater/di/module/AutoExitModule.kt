@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidAutoExitHelper
import co.sodalabs.apkupdater.IAutoExitHelper
import co.sodalabs.apkupdater.di.scopes.ActivityScope
import dagger.Binds
import dagger.Module

@Module
abstract class AutoExitModule {

    @Binds
    @ActivityScope
    abstract fun provideAutoExitHelper(helper: AndroidAutoExitHelper): IAutoExitHelper
}