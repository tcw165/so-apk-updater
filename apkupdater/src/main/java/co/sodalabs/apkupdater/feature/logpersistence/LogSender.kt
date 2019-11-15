package co.sodalabs.apkupdater.feature.logpersistence

import co.sodalabs.apkupdater.feature.logpersistence.api.ILogSenderApi
import co.sodalabs.updaterengine.ISharedSettings
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.feature.logPersistence.ILogSender
import io.reactivex.Completable
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val MIME_TYPE = "text/plain"
private const val PARAM_DEVICE_LOG = "device_log"

class LogSender @Inject constructor(
    private val sharedSettings: ISharedSettings,
    private val senderApi: ILogSenderApi,
    private val schedulers: IThreadSchedulers
) : ILogSender {

    override fun sendLogsToServer(file: File): Completable {
        Timber.i("[LogSender] Sending logs to server")
        return Completable
            .create { emitter ->
                try {
                    val mediaType = MediaType.parse(MIME_TYPE)
                    val requestFile = RequestBody.create(mediaType, file)
                    val deviceId = RequestBody.create(mediaType, sharedSettings.getDeviceId())
                    val fileBody = MultipartBody.Part.createFormData(PARAM_DEVICE_LOG, file.name, requestFile)

                    val apiCall = senderApi.sendLogs(deviceId, fileBody)
                    val response = apiCall.execute()

                    if (emitter.isDisposed) return@create

                    if (response.isSuccessful) {
                        emitter.onComplete()
                    } else {
                        emitter.onError(IllegalStateException("Failed to send log file to server. Error Code: (${response.code()})"))
                    }
                } catch (error: Throwable) {
                    if (emitter.isDisposed) return@create
                    emitter.onError(error)
                }
            }
            .subscribeOn(schedulers.io())
    }
}