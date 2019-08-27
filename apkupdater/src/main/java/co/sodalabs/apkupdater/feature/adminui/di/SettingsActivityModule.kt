@file:Suppress("unused")

package co.sodalabs.apkupdater.feature.adminui.di

import co.sodalabs.apkupdater.di.scopes.FragmentScope
import co.sodalabs.apkupdater.feature.adminui.SettingsFragment
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SettingsActivityModule {

    /**
     * The sub-component of SettingsFragment
     */
    @FragmentScope
    @ContributesAndroidInjector(modules = [
        SettingsFragmentModule::class
    ])
    abstract fun contributeWelcomeActivityInjector(): SettingsFragment
}