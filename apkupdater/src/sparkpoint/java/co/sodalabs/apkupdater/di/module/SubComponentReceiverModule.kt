@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.SparkPointUpdatedReceiver
import co.sodalabs.apkupdater.di.scopes.ReceiverScope
import dagger.Module
import dagger.android.ContributesAndroidInjector

@Module
abstract class SubComponentReceiverModule {

    /**
     * The sub-component of [SparkPointUpdatedReceiver]
     */
    @ReceiverScope
    @ContributesAndroidInjector
    abstract fun contributeSparkpointUpdatedReceiverInjector(): SparkPointUpdatedReceiver
}