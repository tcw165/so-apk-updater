package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Completable
import timber.log.Timber
import java.io.File
import javax.inject.Inject

/**
 * A wrapper class to isolate the ugly operation of calling the adb commands programmatically
 * from rest of the innocent world.
 */
class AdbUtils @Inject constructor(
    val schedulers: IThreadSchedulers
) {

    /**
     * Copies logs from logcat to a file object
     */
    fun copyLogsToFile(file: File, applicationTag: String): Completable {
        return Completable
            .fromCallable {
                try {
                    val cmd = arrayOf("logcat", "-v", "time", "-f", file.absolutePath, "$applicationTag:I")
                    Runtime.getRuntime().exec(cmd)
                } catch (e: Exception) {
                    Timber.e("[AdbUtils] Error while fetching logs from logcat: \n $e")
                }
            }
            .subscribeOn(schedulers.io())
    }
}
