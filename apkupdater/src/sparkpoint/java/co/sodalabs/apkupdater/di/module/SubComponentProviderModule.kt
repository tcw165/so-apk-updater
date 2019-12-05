@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.WorkManagerInitializer
import co.sodalabs.apkupdater.di.scopes.ProviderScope
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentProviderModule {

    /**
     * The sub-component for [WorkManagerInitializer]
     */
    @ProviderScope
    @ContributesAndroidInjector
    abstract fun contributeWorkManagerInitializerInjector(): WorkManagerInitializer
}