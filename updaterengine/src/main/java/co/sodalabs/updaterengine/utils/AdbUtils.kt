package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.IThreadSchedulers
import io.reactivex.Completable
import timber.log.Timber
import java.io.File
import javax.inject.Inject

// Zero means "print all"
const val DEFAULT_MAX_LOG_COUNT = 1000

/**
 * A wrapper class to isolate the ugly operation of calling the adb commands programmatically
 * from rest of the innocent world.
 */
class AdbUtils @Inject constructor(
    private val schedulers: IThreadSchedulers,
    private val processInfoProvider: ProcessInfoProvider
) {

    /**
     * Copies logs from logcat to a file object
     */
    fun copyLogsToFile(
        file: File,
        applicationTag: String = "",
        maxLineCount: Int = DEFAULT_MAX_LOG_COUNT,
        whiteList: List<String> = emptyList(),
        blackList: List<String> = emptyList()
    ): Completable {
        return Completable
            .fromCallable {
                try {
                    val cmd = generateCommand(
                        file,
                        applicationTag,
                        maxLineCount,
                        processInfoProvider.getPidByPackage(whiteList).filterNotNull(),
                        processInfoProvider.getPidByPackage(blackList).filterNotNull()
                    )
                    Timber.i("[AdbUtils] ADB Command: ${cmd.joinToString(" ")}")
                    Runtime.getRuntime().exec(cmd)
                } catch (e: Exception) {
                    Timber.e("[AdbUtils] Error while fetching logs from logcat: \n $e")
                }
            }
            .subscribeOn(schedulers.io())
    }

    fun generateCommand(
        file: File,
        applicationTag: String = "",
        maxLineCount: Int = DEFAULT_MAX_LOG_COUNT,
        whiteListPids: List<Int> = emptyList(),
        blackListPids: List<Int> = emptyList()
    ): Array<String> {
        val pruneList = StringBuilder()
            .apply {
                if (whiteListPids.isNotEmpty()) {
                    append(whiteListPids.joinToString(" "))
                }
                if (whiteListPids.isNotEmpty() && blackListPids.isNotEmpty()) {
                    append(" ")
                }
                if (blackListPids.isNotEmpty()) {
                    append(blackListPids.joinToString(" ") { "~$it" })
                }
            }
            .toString()
        // The 'prune' keyword is used to specify both blacklist and whitelist items.
        // The blacklist items are prefixed with a "~" (tilda sign)
        // Reference: https://developer.android.com/studio/command-line/logcat#options
        val cmd = mutableListOf("logcat", "-v", "time", "-t", "$maxLineCount", "-f", file.absolutePath, "prune", "'$pruneList'")
        if (applicationTag.isNotEmpty()) {
            cmd.add("$applicationTag:I")
        }
        return cmd.toTypedArray()
    }
}
