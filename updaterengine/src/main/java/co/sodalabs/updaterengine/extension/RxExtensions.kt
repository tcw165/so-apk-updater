package co.sodalabs.updaterengine.extension

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Scheduler
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

const val ALWAYS_RETRY = -1

fun <T> Observable<T>.smartRetryWhen(
    retryCount: Int,
    retryDelayInMs: Long,
    retryScheduler: Scheduler,
    shouldRetryPredicate: (error: Throwable) -> Boolean
): Observable<T> {
    val alwaysRetry = retryCount == ALWAYS_RETRY
    val remaining = AtomicInteger(retryCount)
    return this
        .doOnNext {
            // Reset our retry
            remaining.lazySet(retryCount)
        }
        .retryWhen {
            it.flatMap { error ->
                val shouldRetry = shouldRetryPredicate.invoke(error)
                val triesLeft = if (alwaysRetry) {
                    // Always retry
                    1
                } else {
                    remaining.decrementAndGet()
                }

                if (triesLeft <= 0 || !shouldRetry) {
                    Observable.error(error)
                } else {
                    val attempts = retryCount - triesLeft
                    Timber.d("Issuing retry #$attempts")
                    Observable.timer(retryDelayInMs, TimeUnit.MILLISECONDS, retryScheduler)
                }
            }
        }
}

fun Completable.smartRetryWhen(
    retryCount: Int = ALWAYS_RETRY,
    retryDelayInMs: Long = 1000L,
    retryScheduler: Scheduler,
    shouldRetryPredicate: (error: Throwable) -> Boolean
): Completable {
    val remaining = AtomicInteger(retryCount)
    return this
        .doOnComplete {
            // Reset our retry
            remaining.lazySet(retryCount)
        }
        .retryWhen {
            it.flatMap { error ->
                val shouldRetry = shouldRetryPredicate.invoke(error)
                val triesLeft = remaining.decrementAndGet()

                if (triesLeft <= 0 || !shouldRetry) {
                    Flowable.error(error)
                } else {
                    val attempts = retryCount - triesLeft
                    Timber.d("Issuing retry #$attempts")
                    Flowable.timer(retryDelayInMs, TimeUnit.MILLISECONDS, retryScheduler)
                }
            }
        }
}