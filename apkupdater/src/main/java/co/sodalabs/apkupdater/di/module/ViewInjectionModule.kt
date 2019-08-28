@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.view.View

import dagger.Module
import dagger.android.AndroidInjector
import dagger.multibindings.Multibinds

/**
 * The module add [View] as the key to the injector factory. i.e. It tells the
 * Dagger to generate the injector of View.
 */
@Module
abstract class ViewInjectionModule {

    @Multibinds
    internal abstract fun viewInjectorFactories(): Map<Class<out View>, AndroidInjector.Factory<out View>>
}