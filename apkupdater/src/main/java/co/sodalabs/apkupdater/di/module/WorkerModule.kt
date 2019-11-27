@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import androidx.work.RxWorker
import co.sodalabs.updaterengine.di.utils.WorkerFactoryInjector
import co.sodalabs.updaterengine.feature.logPersistence.LogPersistenceWorker
import dagger.Binds
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
import kotlin.reflect.KClass

@Retention(AnnotationRetention.RUNTIME)
@MapKey
annotation class WorkerKey(val value: KClass<out RxWorker>)

@Module
abstract class WorkerModule {

    @Binds
    @IntoMap
    @WorkerKey(LogPersistenceWorker::class)
    internal abstract fun bindLogPersistenceWorker(factory: LogPersistenceWorker.Factory): WorkerFactoryInjector
}