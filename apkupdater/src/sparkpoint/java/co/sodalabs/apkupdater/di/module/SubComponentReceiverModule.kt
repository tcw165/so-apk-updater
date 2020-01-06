@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.SparkPointUpdatedReceiver
import co.sodalabs.apkupdater.UpdaterSelfUpdateReceiver
import co.sodalabs.apkupdater.WorkOnAppLaunchInitializer
import co.sodalabs.apkupdater.di.scopes.ReceiverScope
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentReceiverModule {

    @ReceiverScope
    @ContributesAndroidInjector
    abstract fun contributeUpdaterSelfUpdateReceiverInjector(): UpdaterSelfUpdateReceiver

    @ReceiverScope
    @ContributesAndroidInjector
    abstract fun contributeSparkpointUpdatedReceiverInjector(): SparkPointUpdatedReceiver

    @ReceiverScope
    @ContributesAndroidInjector
    abstract fun contributeWorkOnBootInitializerInjector(): WorkOnAppLaunchInitializer
}