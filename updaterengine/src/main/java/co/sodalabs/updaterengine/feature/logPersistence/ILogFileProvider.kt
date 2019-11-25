package co.sodalabs.updaterengine.feature.logPersistence

import java.io.File

interface ILogFileProvider {
    val logFile: File
    val tempLogFile: File
}