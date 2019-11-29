package co.sodalabs.updaterengine.di

import androidx.work.ListenableWorker
import dagger.internal.Preconditions.checkNotNull
import timber.log.Timber

object WorkerInjection {

    /**
     * Inject the [ListenableWorker] with the field injection approach.
     *
     * Note: Two ways to inject the dependencies:
     * 1. Constructor injection.
     * 2. Field injection.
     *
     * The constructor injection requires more personalized factory boilerplate
     * code, while the field injection only requires a fixed amount of modules
     * setup and a helper function like this.
     *
     * This doesn't work with the constructor injection.
     */
    fun inject(worker: ListenableWorker) {
        checkNotNull(worker, "worker")
        val hasWorkerInjector = findHasWorkerInjector(worker)
        val workerInjector = hasWorkerInjector.workerInjector()
        checkNotNull(workerInjector, "%s.workerInjector() returned null", workerInjector.javaClass.canonicalName)

        workerInjector.inject(worker)
    }

    private fun findHasWorkerInjector(worker: ListenableWorker): HasWorkerInjector {
        Timber.d("[Injection] Find worker injector for $worker...")
        val context = worker.applicationContext
        return context as? HasWorkerInjector
            ?: throw IllegalArgumentException(String.format("No injector was found for %s", worker.javaClass.canonicalName))
    }
}