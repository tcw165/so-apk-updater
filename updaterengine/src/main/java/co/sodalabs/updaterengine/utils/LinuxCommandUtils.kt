package co.sodalabs.updaterengine.utils

import timber.log.Timber
import java.util.Scanner
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.log

/**
 * A wrapper class to isolate the ugly operation of calling the linux commands programmatically
 * from rest of the innocent world.
 */

private const val EMPTY_STRING = ""

/**
 * The supported argument for 'du' linux command on Big-Tab.
 *
 * @param -c Display a grand total.
 * @param -a Display an entry for each file in a file hierarchy.
 * @param -k Display block counts in 1024-byte (1-Kbyte) blocks.
 */
private const val CMD_GET_CACHE_DISK_USAGE = "du -k /storage/emulated/legacy"
private const val CMD_GET_DATA_DISK_USAGE = "du -k /data/data"
private const val CMD_GET_SYSTEM_DISK_USAGE_STATS = "df"
private const val CMD_ADB_REBOOT = "reboot"
private const val CMD_ADB_REBOOT_TO_RECOVERY = "recovery"

private const val LOG_FOOTER = "----------------------------"
private const val HEADER_OR_FOOTER_SIZE = LOG_FOOTER.length

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
        val sb = StringBuilder(log.length + 2 * HEADER_OR_FOOTER_SIZE)
        sb.appendln("------------ df ------------")
        sb.appendln(log)
        sb.appendln(LOG_FOOTER)
        println(sb.toString())

        return log
    }

    /**
     * Logs disk usage by folders owned by our system apps
     */
    fun logDiskUsageByApps(onlySodaApps: Boolean = false): String {
        val logForCache = try {
            // FIXME: Align the directory (some line should have less space and
            //  some should have more)
            execCmd(CMD_GET_CACHE_DISK_USAGE)
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
                .smartlyJoinToString()
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing App Disk Usage command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        val logForData = try {
            // FIXME: Align the directory (some line should have less space and
            //  some should have more)
            execCmd(CMD_GET_DATA_DISK_USAGE)
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
                .smartlyJoinToString()
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing App Disk Usage command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        // IMPORTANT: Using println instead of Timber to avoid custom tags and timestamps
        val sb = StringBuilder(logForCache.length + 3 * HEADER_OR_FOOTER_SIZE)
        sb.appendln("------------ du ------------")
        sb.appendln(logForCache)
        sb.appendln("------------ du ------------")
        sb.appendln(logForData)
        sb.appendln(LOG_FOOTER)
        println(sb.toString())

        return logForCache
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

    /**
     * Just align the number for readability.
     */
    private fun List<AppDir>.smartlyJoinToString(): String {
        var maxDigits = 0
        this.forEach {
            val digit = ceil(log(it.size.toDouble(), 10.0)).toInt()
            maxDigits = maxDigits.coerceAtLeast(digit)
        }
        // 1 is the padding.
        val formatter = "-%${1 + maxDigits}dKB %s"

        return this.joinToString(
            separator = System.lineSeparator(),
            transform = { formatter.format(it.size, it.path) }
        )
    }

    fun performAdbReboot() {
        val log = try {
            execCmd(CMD_ADB_REBOOT)
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing adb reboot command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        // IMPORTANT: Using println instead of Timber to avoid custom tags and timestamps
        val sb = StringBuilder(log.length + 2 * HEADER_OR_FOOTER_SIZE)
        sb.appendln("------------ adb reboot ------------")
        sb.appendln(log)
        sb.appendln(LOG_FOOTER)
        println(sb.toString())
    }

    fun performAdbRebootToRecovery() {
        val log = try {
            execCmd(CMD_ADB_REBOOT_TO_RECOVERY)
        } catch (e: Exception) {
            Timber.e("[LinuxCommandUtils] Error while executing adb reboot command: ${e.message}")
            e.printStackTrace()
            EMPTY_STRING
        }
        // IMPORTANT: Using println instead of Timber to avoid custom tags and timestamps
        val sb = StringBuilder(log.length + 2 * HEADER_OR_FOOTER_SIZE)
        sb.appendln("------------ adb reboot recovery ------------")
        sb.appendln(log)
        sb.appendln(LOG_FOOTER)
        println(sb.toString())
    }
}