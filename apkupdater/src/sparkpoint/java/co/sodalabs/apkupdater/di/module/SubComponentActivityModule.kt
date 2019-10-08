@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.di.scopes.ActivityScope
import co.sodalabs.apkupdater.feature.adminui.SettingsActivity
import co.sodalabs.apkupdater.feature.adminui.di.SettingsActivityModule
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentActivityModule {

    /**
     * The sub-component of SettingsActivity
     */
    @ActivityScope
    @ContributesAndroidInjector(modules = [
        SettingsActivityModule::class,
        AutoExitModule::class,
        DialogModule::class
    ])
    abstract fun contributeWelcomeActivityInjector(): SettingsActivity
}