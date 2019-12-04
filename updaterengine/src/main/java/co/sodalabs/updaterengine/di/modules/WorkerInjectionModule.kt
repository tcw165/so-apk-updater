@file:Suppress("unused")

package co.sodalabs.updaterengine.di.modules

import androidx.work.ListenableWorker
import androidx.work.Worker
import dagger.Module
import dagger.android.AndroidInjector
import dagger.multibindings.Multibinds

/**
 * The module add [Worker] as the key to the injector factory. i.e. It tells the
 * Dagger to generate the injector for [Worker].
 */
@Module
abstract class WorkerInjectionModule {

    @Multibinds
    internal abstract fun workerInjectorFactories(): Map<Class<out ListenableWorker>, AndroidInjector.Factory<out ListenableWorker>>
}