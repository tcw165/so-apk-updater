package co.sodalabs.apkupdater

import androidx.appcompat.app.AppCompatActivity
import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Completable
import io.reactivex.Single
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class AndroidAutoExitHelper @Inject constructor(
    private val activity: AppCompatActivity,
    private val touchTracker: ITouchTracker,
    private val systemLauncherUtil: ISystemLauncherUtil,
    private val schedulers: IThreadSchedulers
) : IAutoExitHelper {

    override fun startAutoExitCountDown(
        timeoutMillis: Long
    ): Completable {
        return touchTracker.observeAnyTouches()
            .map { Unit }
            // Use the first trigger to initialize the timer.
            .startWith(Unit)
            // The timer should reset when the user interacts with the UI
            .switchMapSingle {
                Timber.v("[Dismiss] Set a auto-exit timeout after '$timeoutMillis' milliseconds")
                Single.timer(timeoutMillis, TimeUnit.MILLISECONDS, schedulers.computation())
            }
            .flatMapCompletable { popLayerCompletable() }
    }

    private fun popLayerCompletable(): Completable {
        return Completable
            .fromAction {
                Timber.v("[Dismiss] Pop layer cause it's timeout!")
                systemLauncherUtil.startSodaLabsLauncherIfInstalled()
            }
            .subscribeOn(schedulers.io())
    }
}