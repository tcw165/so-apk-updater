package co.sodalabs.apkupdater.feature.logpersistence

import android.content.Context
import co.sodalabs.updaterengine.feature.logPersistence.ILogFileProvider
import co.sodalabs.updaterengine.feature.logPersistence.LogsPersistenceConstants
import co.sodalabs.updaterengine.utils.StorageUtils
import java.io.File
import javax.inject.Inject

class LogFileProvider @Inject constructor(
    val context: Context
) : ILogFileProvider {

    override val logFile: File by lazy {
        val dir = File(StorageUtils.getCacheDirectory(context, false), LogsPersistenceConstants.LOG_DIR)
        File(dir, LogsPersistenceConstants.LOG_FILE)
    }

    override val tempLogFile: File by lazy {
        val dir = StorageUtils.getCacheDirectory(context, false)
        File(dir, LogsPersistenceConstants.TEMP_LOG_BUFFER_FILE)
    }
}