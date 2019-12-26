package co.sodalabs.updaterengine.utils

import timber.log.Timber
import java.util.Scanner
import javax.inject.Inject

/**
 * A wrapper class to isolate the ugly operation of calling the linux commands programmatically
 * from rest of the innocent world.
 */

private const val EMPTY_STRING = ""

private const val CMD_GET_SODA_DIR_DISK_USAGE = "du /storage/emulated/legacy"
private const val CMD_GET_SYSTEM_DISK_USAGE_STATS = "df"

class LinuxCommandUtils @Inject constructor() {

    /**
     * Print device's storage stats to logs
     */
    fun logDiskUsage(): String {
        val log = try {
            execCmd(CMD_GET_SYSTEM_DISK_USAGE_STATS)
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing disk usage command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        // IMPORTANT: Using println instead of Timber to avoid custom tags and timestamps
        println("Start Disk Usage Log\n$log\nEnd Disk Usage Log")
        return log
    }

    /**
     * Logs disk usage by folders owned by our system apps
     */
    fun logDiskUsageByApps(onlySodaApps: Boolean = false): String {
        val log = try {
            execCmd(CMD_GET_SODA_DIR_DISK_USAGE)
                .lines()
                .filter { it.isNotEmpty() }
                .map { AppDir.fromString(it) }
                .let { dirs ->
                    if (onlySodaApps) {
                        dirs.filter { it.path.contains("co.sodalabs") }
                    } else {
                        dirs
                    }
                }
                .sortedByDescending { it.size }
                .joinToString("\n")
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing App Disk Usage command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        // IMPORTANT: Using println instead of Timber to avoid custom tags and timestamps
        println("Start Sodalab App Disk Usage Log\n$log\nEnd Sodalab App Disk Usage Log")
        return log
    }

    /**
     * Not needed for now but will be used if we decide to log these stats to heartbeat
     * or use it for auto cleanup
     */
    fun collectDiskUsageLogs(): List<DiskUsageStat> {
        // NOTE: It is important to have some delay after every call that depends on
        // command execution on the runtime.
        val text = logDiskUsage()
        if (text.isEmpty()) {
            return emptyList()
        }

        val lines = text.lines()
        return lines
            .takeLast(lines.count() - 1)
            .mapNotNull { DiskUsageStat.fromString(it) }
    }

    /**
     * A wrapper to execute commands and return console results in a cleaner way
     */
    private fun execCmd(cmd: String): String {
        Timber.i("[LinuxCommandUtils] Linux Command: $cmd")
        val scanner = Scanner(Runtime.getRuntime().exec(cmd).inputStream)
        val sb = StringBuilder()
        while (scanner.hasNextLine()) {
            sb.appendln(scanner.nextLine())
        }

        return if (sb.isNotEmpty()) {
            sb.toString()
        } else {
            EMPTY_STRING
        }
    }
}