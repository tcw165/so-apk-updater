package co.sodalabs.updaterengine

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import android.os.SystemClock
import android.support.v4.app.JobIntentService
import co.sodalabs.updaterengine.data.Apk
import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.net.UpdaterApi
import co.sodalabs.updaterengine.utils.BuildUtils
import co.sodalabs.updaterengine.utils.versionCodeForPackage
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

private const val HTTP_READ_WRITE_TIMEOUT = 25L
private const val HTTP_CONNECT_TIMEOUT = 15L

class UpdaterService : JobIntentService() {

    companion object {
        private const val INITIAL_CHECK_DELAY = 5000L
        private const val UPDATE_CHECK_JOB_ID = 0xfedcba
        internal const val EXTRA_UPDATE_URL = "co.sodalabs.updaterengine.UpdaterService.update_url"
        internal const val EXTRA_AUTO_DOWNLOAD = "co.sodalabs.updaterengine.UpdaterService.auto_download"

        fun checkNow(context: Context, updateUrl: String, autoDownload: Boolean) {
            val intent = Intent(context, UpdaterService::class.java)
            intent.putExtra(EXTRA_UPDATE_URL, updateUrl)
            intent.putExtra(EXTRA_AUTO_DOWNLOAD, autoDownload)
            context.startService(intent)
        }

        fun schedule(context: Context, interval: Long, updateUrl: String, autoDownload: Boolean) {
            if (Build.VERSION.SDK_INT < 21) {
                val intent = Intent(context, UpdaterService::class.java)
                intent.putExtra(EXTRA_UPDATE_URL, updateUrl)
                intent.putExtra(EXTRA_AUTO_DOWNLOAD, autoDownload)

                val pendingIntent = PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                alarmManager.cancel(pendingIntent)
                alarmManager.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + INITIAL_CHECK_DELAY,
                    interval,
                    pendingIntent
                )

                Timber.d("Update scheduler alarm set")
            } else {

                Timber.d("Using android-21 JobScheduler for updates")

                val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
                val componentName = ComponentName(context, UpdateJobService::class.java)
                val bundle = PersistableBundle()
                bundle.putString(EXTRA_UPDATE_URL, updateUrl)
                bundle.putInt(EXTRA_AUTO_DOWNLOAD, if (autoDownload) 1 else 0)

                val builder = JobInfo.Builder(UPDATE_CHECK_JOB_ID, componentName)
                    .setRequiresDeviceIdle(true)
                    .setPeriodic(interval)
                    .setExtras(bundle)

                if (Build.VERSION.SDK_INT >= 26) {
                    builder.setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                }

                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)

                jobScheduler.cancel(UPDATE_CHECK_JOB_ID)
                jobScheduler.schedule(builder.build())
                Timber.d("Update scheduler alarm set")
            }
        }
    }

    private val connectivityManager by lazy {
        getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private fun getUpdaterApi(updateUrl: String): UpdaterApi {
        val builder = Moshi.Builder()
        val retrofit = provideSparkpointRetrofit(builder.build(), provideOkHttpClient(), updateUrl)
        return retrofit.create(UpdaterApi::class.java)
    }

    private fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor()
        logging.level =
                if (BuildUtils.isDebug()) HttpLoggingInterceptor.Level.BODY else HttpLoggingInterceptor.Level.NONE

        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .writeTimeout(HTTP_READ_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(HTTP_READ_WRITE_TIMEOUT, TimeUnit.SECONDS)
            .connectTimeout(HTTP_CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private fun provideSparkpointRetrofit(moshi: Moshi, okHttpClient: OkHttpClient, updateUrl: String): Retrofit {
        val uri = Uri.parse(updateUrl)
        val scheme = uri.scheme ?: throw IllegalArgumentException("updateUrl does not have a scheme.")
        val baseUrl = scheme + "://" + uri.host

        return Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .build()
    }

    override fun onHandleWork(intent: Intent) {
        if (!hasNetworkConnection()) {
            Timber.w("No network available, skipping update check.")
            return
        }

        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)

        val updateUrl =
            intent.getStringExtra(EXTRA_UPDATE_URL) ?: throw IllegalArgumentException("Must provide update url.")
        val autoDownload = intent.getBooleanExtra(EXTRA_AUTO_DOWNLOAD, false)

        val apiRequest = getUpdaterApi(updateUrl).getAppUpdate(updateUrl)

        try {
            val apiResponse = apiRequest.execute()

            if (apiResponse.isSuccessful) {
                val appUpdate = apiResponse.body()

                Timber.i("getAppUpdate result: ${apiResponse.raw().code()}\n$appUpdate")
                if (appUpdate != null) {
                    checkIfUpdateIsAvailable(appUpdate, autoDownload)
                }
            }
        } catch (e: IOException) {
            Timber.e(e)
        } catch (e: SSLException) {
            Timber.e(e)
        } catch (e: SocketTimeoutException) {
            Timber.e(e)
        }

        stopSelf(UPDATE_CHECK_JOB_ID)
    }

    private fun checkIfUpdateIsAvailable(appUpdate: AppUpdate, autoDownload: Boolean) {
        val versionCode = packageManager.versionCodeForPackage(appUpdate.packageName)

        if (appUpdate.versionCode > versionCode) {
            Timber.i("Update found: $versionCode -> ${appUpdate.versionCode}\n${appUpdate.updateMessage}")

            val uri = Uri.parse(appUpdate.downloadUrl)
            val apk = Apk(
                uri,
                appUpdate.packageName,
                appUpdate.versionName,
                appUpdate.versionCode,
                appUpdate.hash,
                apkName = appUpdate.fileName
            )
            ApkUpdater.singleton().notifyUpdateAvailable(apk, appUpdate.updateMessage)

            if (autoDownload) {
                ApkUpdater.singleton().downloadApk(appUpdate)
            }
        } else {
            Timber.d("Already up to date install $appUpdate")
        }
    }

    private fun hasNetworkConnection(): Boolean {
        return connectivityManager.activeNetworkInfo?.isConnectedOrConnecting == true
    }
}