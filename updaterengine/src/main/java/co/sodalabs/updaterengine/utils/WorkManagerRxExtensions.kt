@file:Suppress("SENSELESS_COMPARISON")

package co.sodalabs.updaterengine.utils

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.reactivex.Observable
import java.util.UUID

/**
 * For some reason, the [androidx.work.WorkManager] SDK only allows us to observe events using AAC's
 * LiveData, this file contains a set of handy extension functions that help us observe events using
 * RxJava. The methods try to keep the same scheme as the ones provided for LiveData.
 * */

fun WorkManager.getWorkInfoByIdObservable(requestId: UUID): Observable<WorkInfo> {
    return Observable
        .create<WorkInfo> { emitter ->
            val observer = Observer<WorkInfo?> { infoOpt ->
                // Note: The LiveData could give nullable payload.
                infoOpt?.let { info ->
                    emitter.onNext(info)
                }
            }
            val workInfo: LiveData<WorkInfo?> = this.getWorkInfoByIdLiveData(requestId)
            if (workInfo == null) {
                emitter.onError(NoSuchElementException("No worker found with id: $requestId"))
            }
            workInfo.observeForever(observer)

            emitter.setCancellable {
                workInfo.removeObserver(observer)
            }
        }
}

fun WorkManager.getWorkInfosByTagObservable(tag: String): Observable<MutableList<WorkInfo>> {
    return Observable
        .create<MutableList<WorkInfo>> { emitter ->
            val observer = { infos: MutableList<WorkInfo> -> emitter.onNext(infos) }
            val workInfo = this.getWorkInfosByTagLiveData(tag)
            if (workInfo == null) {
                emitter.onError(NoSuchElementException("No worker found with tag: $tag"))
            }
            workInfo.observeForever(observer)

            emitter.setCancellable {
                workInfo.removeObserver(observer)
            }
        }
}

fun WorkManager.getWorkInfosForUniqueWorkObservable(uniqueWorkName: String): Observable<MutableList<WorkInfo>> {
    return Observable
        .create<MutableList<WorkInfo>> { emitter ->
            val observer = { infos: MutableList<WorkInfo> -> emitter.onNext(infos) }
            val workInfo = this.getWorkInfosForUniqueWorkLiveData(uniqueWorkName)
            if (workInfo == null) {
                emitter.onError(NoSuchElementException("No worker found with name: $uniqueWorkName"))
            }
            workInfo.observeForever(observer)

            emitter.setCancellable {
                workInfo.removeObserver(observer)
            }
        }
}
