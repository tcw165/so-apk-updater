@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.IAppPreference
import co.sodalabs.apkupdater.PreferenceProps
import co.sodalabs.apkupdater.di.ApplicationScope
import co.sodalabs.apkupdater.feature.checker.SparkPointUpdatesChecker
import co.sodalabs.apkupdater.feature.checker.api.ISparkPointUpdateCheckApi
import co.sodalabs.apkupdater.feature.heartbeat.SparkPointHeartBeater
import co.sodalabs.apkupdater.feature.heartbeat.api.ISparkPointHeartBeatApi
import co.sodalabs.updaterengine.AppUpdaterHeartBeater
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.AppUpdatesDownloader
import co.sodalabs.updaterengine.AppUpdatesInstaller
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.feature.downloader.DefaultUpdatesDownloader
import co.sodalabs.updaterengine.feature.installer.DefaultAppUpdatesInstaller
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

private const val HTTP_READ_WRITE_TIMEOUT = 25L
private const val HTTP_CONNECT_TIMEOUT = 15L

@Module
class UpdaterModule @Inject constructor(
    private val context: Context,
    private val appPreferences: IAppPreference,
    private val schedulers: IThreadSchedulers
) {

    // FIXME: Use @Bind or DaggerAndroid way to avoid boilerplate code.
    private val checker by lazy { SparkPointUpdatesChecker(context, schedulers) }
    // Use default download and installer from the update engine.
    private val downloader by lazy { DefaultUpdatesDownloader(context, schedulers) }
    private val installer by lazy { DefaultAppUpdatesInstaller(context, schedulers) }
    private val heartBeater by lazy { SparkPointHeartBeater(context, schedulers) }

    @Provides
    @ApplicationScope
    fun provideAppUpdatesChecker(): AppUpdatesChecker = checker

    @Provides
    @ApplicationScope
    fun provideAppUpdatesDownloader(): AppUpdatesDownloader = downloader

    @Provides
    @ApplicationScope
    fun provideAppUpdatesInstaller(): AppUpdatesInstaller = installer

    @Provides
    @ApplicationScope
    fun provideHeartBeater(): AppUpdaterHeartBeater = heartBeater

    private val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        val timeoutConnect = appPreferences.getInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS).toLong()
        val timeoutRead = appPreferences.getInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS).toLong()
        val timeoutWrite = appPreferences.getInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS).toLong()

        Timber.v("[Updater] Setup HTTP client with connect timeout ($timeoutConnect seconds)")
        Timber.v("[Updater] Setup HTTP client with read timeout ($timeoutRead seconds)")
        Timber.v("[Updater] Setup HTTP client with write timeout ($timeoutWrite seconds)")

        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .connectTimeout(timeoutConnect, TimeUnit.SECONDS)
            .readTimeout(timeoutRead, TimeUnit.SECONDS)
            .writeTimeout(timeoutWrite, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    private val moshi by lazy {
        // TODO: Shall we specify the Moshi adapter?
        Moshi.Builder()
            .build()
    }

    private val retrofit by lazy {
        Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl(BuildConfig.BASE_URL)
            .client(okHttpClient)
            .build()
    }

    @Provides
    @ApplicationScope
    fun provideAppUpdateCheckAPI(): ISparkPointUpdateCheckApi = retrofit.create(ISparkPointUpdateCheckApi::class.java)

    @Provides
    @ApplicationScope
    fun provideHeartBeatAPI(): ISparkPointHeartBeatApi = retrofit.create(ISparkPointHeartBeatApi::class.java)
}