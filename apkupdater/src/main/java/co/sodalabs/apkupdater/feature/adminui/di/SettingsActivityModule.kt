@file:Suppress("unused")

package co.sodalabs.apkupdater.feature.adminui.di

import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.apkupdater.di.scopes.FragmentScope
import co.sodalabs.apkupdater.feature.adminui.SettingsActivity
import co.sodalabs.apkupdater.feature.adminui.SettingsFragment
import dagger.Binds
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SettingsActivityModule {

    @Binds
    abstract fun provideActivity(
        activity: SettingsActivity
    ): AppCompatActivity

    /**
     * The sub-component of SettingsFragment
     */
    @FragmentScope
    @ContributesAndroidInjector(modules = [
        SettingsFragmentModule::class
    ])
    abstract fun contributeWelcomeActivityInjector(): SettingsFragment
}