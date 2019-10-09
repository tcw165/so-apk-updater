package co.sodalabs.apkupdater

import io.reactivex.Completable

/**
 * The companion helpers (plugins) for the activity to exit automatically.
 */
interface IAutoExitHelper {
    fun startAutoExitCountDown(timeoutMillis: Long): Completable
}