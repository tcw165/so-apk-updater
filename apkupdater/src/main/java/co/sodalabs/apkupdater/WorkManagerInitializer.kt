package co.sodalabs.apkupdater

import android.annotation.SuppressLint
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.work.Configuration
import androidx.work.WorkManager
import co.sodalabs.apkupdater.feature.watchdog.IForegroundAppWatchdogLauncher
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Can't request the work on Application#Create cause there's high chance to
 * IllegalStateException. Therefore, we request the work right after the work
 * manager initialization!
 */
class WorkManagerInitializer : ContentProvider() {

    @Inject
    lateinit var foregroundAppWatchdogLauncher: IForegroundAppWatchdogLauncher

    private val tag = WorkManagerInitializer::class.java.simpleName

    @SuppressLint("LogNotTimber")
    override fun onCreate(): Boolean {
        AndroidInjection.inject(this)

        // Using LogCat instead Timber here cause the injection for Timber is NOT
        // ready yet!
        Log.v(tag, "[WorkManagerInitializer] Initializing...")

        context?.let { safeContext ->
            // Initialize WorkManager with the default configuration.
            WorkManager.initialize(safeContext, Configuration.Builder().build())

            initForegroundAppWatchdog()
        } ?: throw IllegalStateException("[WorkManagerInitializer] No way the null context")

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?
    ): Cursor? {
        return null
    }

    override fun getType(
        uri: Uri
    ): String? {
        return null
    }

    override fun insert(
        uri: Uri,
        values: ContentValues?
    ): Uri? {
        return null
    }

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<String>?
    ): Int {
        return 0
    }

    // Foreground App Watchdog ////////////////////////////////////////////////

    private fun initForegroundAppWatchdog() {
        foregroundAppWatchdogLauncher.scheduleForegroundProcessValidation()
    }
}