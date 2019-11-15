package co.sodalabs.updaterengine.feature.logPersistence

import io.reactivex.Completable
import java.io.File

interface ILogSender {
    fun sendLogsToServer(file: File): Completable
}