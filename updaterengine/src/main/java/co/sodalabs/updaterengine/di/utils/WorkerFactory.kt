package co.sodalabs.updaterengine.di.utils

import android.content.Context
import androidx.work.RxWorker
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import javax.inject.Inject
import javax.inject.Provider

/**
 * Dagger.Android does not support injection into Android's native components out-of-the-box right
 * now. This factory assists Dagger with injection for [WorkManager]'s [Worker] items, hence acting
 * more like an extension to Dagger. This is a complicated solution and only around due to the lack
 * of a better alternative. For that reason, it should be deprecated as soon as Dagger Android adds
 * support for native component injection.
 *
 * As always, injecting dependencies to Android's components is a fun process.
 *
 * To overcome the limitation, we need to use a double-factory pattern.
 *
 * First factory is the [WorkerFactory] provided by the Android SDK which is similar to the
 * [androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory]. Then we also need to provide
 * a child factory that takes the items that are only available at the runtime ([Context] and
 * [WorkerParameters]) and use them to assist the [WorkerFactory].
 *
 * Another important step is to disable the default [androidx.work.impl.WorkManagerInitializer]
 * in the Manifest and then manually initialize it with our factory instance in the
 * [co.sodalabs.sparkpoint.PlayerApp]
 *
 * References:
 * - https://proandroiddev.com/dagger-2-setup-with-workmanager-a-complete-step-by-step-guild-bb9f474bde37
 * - https://android.jlelse.eu/injecting-into-workers-android-workmanager-and-dagger-948193c17684
 */
class WorkerFactory @Inject constructor(
    private val workerFactories: Map<Class<out RxWorker>, @JvmSuppressWildcards Provider<ChildWorkerFactory>>
) : WorkerFactory() {
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): RxWorker? {
        // IMPORTANT: While this is standard pattern recommended by Google for DI
        // pertaining to ViewModels, new Androidx Fragments, WorkManager Workers,
        // and other Android specific components. This uses reflection and using
        // these child components outside of framework bounds is very dangerous,
        // specially when using ProGuard.
        // So, DON'T TRY TO USE THE RxWORKER AS AN INDEPENDENT OBSERVABLE!
        val foundEntry = workerFactories.entries
            .find { Class.forName(workerClassName).isAssignableFrom(it.key) }
        // While this IllegalArgumentException is there as a safeguard, in actual implementation,
        // this factory will fail at compile time if you forget to bind an inject Worker item
        val factoryProvider = foundEntry?.value
            ?: throw IllegalArgumentException("unknown worker class name: $workerClassName")
        return factoryProvider.get().create(appContext, workerParameters)
    }
}

/**
 * Every [Worker] must provide a factory that implements this interface to assist [WorkerFactory]
 * with the injection
 */
interface ChildWorkerFactory {
    fun create(appContext: Context, params: WorkerParameters): RxWorker
}