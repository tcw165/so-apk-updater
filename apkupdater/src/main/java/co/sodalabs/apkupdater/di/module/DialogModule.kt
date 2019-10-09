@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.AndroidPasscodeDialogFactory
import co.sodalabs.apkupdater.IPasscodeDialogFactory
import co.sodalabs.apkupdater.di.scopes.ActivityScope
import dagger.Binds
import dagger.Module

@Module
abstract class DialogModule {

    @Binds
    @ActivityScope
    abstract fun providePasscodeDialogFactory(
        factory: AndroidPasscodeDialogFactory
    ): IPasscodeDialogFactory
}