package co.sodalabs.updaterengine.di

import androidx.work.ListenableWorker
import dagger.android.AndroidInjector

/**
 * Has worker injector.
 */
interface HasWorkerInjector {
    /**
     * Returns an android injector of workers.
     */
    fun workerInjector(): AndroidInjector<ListenableWorker>
}