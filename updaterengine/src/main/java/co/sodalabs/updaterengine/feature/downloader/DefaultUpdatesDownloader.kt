package co.sodalabs.updaterengine.feature.downloader

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.Intervals
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import io.reactivex.Single
import java.io.File
import java.util.concurrent.TimeUnit

class DefaultUpdatesDownloader constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesDownloader {

    override fun download(
        updates: List<AppUpdate>
    ): Single<List<Apk>> {
        DownloadJobIntentService.downloadNow(context, updates)

        // Observe the result via the local broadcast
        val intentFilter = IntentFilter(IntentActions.ACTION_DOWNLOAD_UPDATES)
        return RxLocalBroadcastReceiver.bind(context, intentFilter)
            .firstOrError()
            .timeout(Intervals.TIMEOUT_DOWNLOAD_HR, TimeUnit.HOURS, schedulers.computation())
            .map { intent -> extractDownloadResult(updates, intent) }
    }

    private fun extractDownloadResult(
        updates: List<AppUpdate>,
        intent: Intent
    ): List<Apk> {
        val error = intent.getSerializableExtra(IntentActions.PROP_ERROR) as? Throwable
        return error?.let { err ->
            throw err
        } ?: kotlin.run {
            val fileURIs = intent.getParcelableArrayListExtra<Uri>(IntentActions.PROP_APP_DOWNLOAD_FILE_URIS)
            val fileToUpdateIndices = intent.getIntegerArrayListExtra(IntentActions.PROP_APP_DOWNLOAD_FILE_URIS_TO_UPDATE_INDICES)
            val apks = mutableListOf<Apk>()

            for (i in 0 until fileURIs.size) {
                val uri = fileURIs[i]
                val file = File(uri.path)
                val fileToUpdateIndex = fileToUpdateIndices[i]
                val fileFromUpdate = updates[fileToUpdateIndex]
                apks.add(Apk(file, fileFromUpdate))
            }

            apks
        }
    }
}