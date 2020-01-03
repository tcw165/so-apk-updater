@file:Suppress("unused")

package co.sodalabs.apkupdater.feature.adminui.di

import co.sodalabs.apkupdater.di.scopes.FragmentScope
import co.sodalabs.apkupdater.feature.adminui.ISettingsScreen
import co.sodalabs.apkupdater.feature.adminui.SettingsFragment
import dagger.Binds
import dagger.Module

@Module
abstract class SettingsFragmentModule {

    @FragmentScope
    @Binds
    abstract fun castFragmentToScreen(
        fragment: SettingsFragment
    ): ISettingsScreen
}