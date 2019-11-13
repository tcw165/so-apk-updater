@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.apkupdater.di.scopes.ApplicationScope
import co.sodalabs.apkupdater.feature.checker.api.ISparkPointUpdateCheckApi
import co.sodalabs.apkupdater.feature.heartbeat.api.ISparkPointHeartBeatApi
import co.sodalabs.apkupdater.net.HostResolutionInterceptor
import co.sodalabs.apkupdater.utils.BuildUtils
import co.sodalabs.apkupdater.utils.HttpTimberLogger
import co.sodalabs.updaterengine.CHECK_HTTP_CLIENT
import co.sodalabs.updaterengine.DOWNLOAD_HTTP_CLIENT
import co.sodalabs.updaterengine.IAppPreference
import co.sodalabs.updaterengine.PreferenceProps
import co.sodalabs.updaterengine.jsonadapter.FileAdapter
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named

@Module
class NetworkModule {

    private val moshi by lazy {
        // TODO: Shall we specify the Moshi adapter?
        Moshi.Builder()
            .add(FileAdapter())
            .build()
    }

    @Provides
    @ApplicationScope
    fun provideJsonBuilder(): Moshi = moshi

    @Provides
    @ApplicationScope
    @Named(DOWNLOAD_HTTP_CLIENT)
    fun provideDownloadHttpClient(
        appPreference: IAppPreference
    ): OkHttpClient {
        val hostResolutionInterceptor = provideHostResolutionInterceptor()
        // Only show headers for downloads
        val logsInterceptor = HttpLoggingInterceptor(HttpTimberLogger())
        logsInterceptor.level = HttpLoggingInterceptor.Level.HEADERS

        val timeoutConnect = appPreference.getInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS).toLong()
        val timeoutRead = appPreference.getInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS).toLong()
        val timeoutWrite = appPreference.getInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS).toLong()

        Timber.v("[Updater] Setup download HTTP client with connect timeout ($timeoutConnect seconds)")
        Timber.v("[Updater] Setup download HTTP client with read timeout ($timeoutRead seconds)")
        Timber.v("[Updater] Setup download HTTP client with write timeout ($timeoutWrite seconds)")

        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .connectTimeout(timeoutConnect, TimeUnit.SECONDS)
            .readTimeout(timeoutRead, TimeUnit.SECONDS)
            .writeTimeout(timeoutWrite, TimeUnit.SECONDS)
            .addInterceptor(logsInterceptor)
            // Smartly add host to speed up the domain resolution
            .addInterceptor(hostResolutionInterceptor)
            .build()
    }

    @Provides
    @ApplicationScope
    @Named(CHECK_HTTP_CLIENT)
    fun provideCheckHttpClient(
        appPreference: IAppPreference
    ): OkHttpClient {
        val hostResolutionInterceptor = provideHostResolutionInterceptor()
        val logsInterceptor = provideLogsInterceptor()

        val timeoutConnect = appPreference.getInt(PreferenceProps.NETWORK_CONNECTION_TIMEOUT_SECONDS, BuildConfig.CONNECT_TIMEOUT_SECONDS).toLong()
        val timeoutRead = appPreference.getInt(PreferenceProps.NETWORK_READ_TIMEOUT_SECONDS, BuildConfig.READ_TIMEOUT_SECONDS).toLong()
        val timeoutWrite = appPreference.getInt(PreferenceProps.NETWORK_WRITE_TIMEOUT_SECONDS, BuildConfig.WRITE_TIMEOUT_SECONDS).toLong()

        Timber.v("[Updater] Setup HTTP client with connect timeout ($timeoutConnect seconds)")
        Timber.v("[Updater] Setup HTTP client with read timeout ($timeoutRead seconds)")
        Timber.v("[Updater] Setup HTTP client with write timeout ($timeoutWrite seconds)")

        return OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .retryOnConnectionFailure(true)
            .cache(null)
            .connectTimeout(timeoutConnect, TimeUnit.SECONDS)
            .readTimeout(timeoutRead, TimeUnit.SECONDS)
            .writeTimeout(timeoutWrite, TimeUnit.SECONDS)
            .addInterceptor(logsInterceptor)
            // Smartly add host to speed up the domain resolution
            .addInterceptor(hostResolutionInterceptor)
            .build()
    }

    private fun provideLogsInterceptor(): Interceptor {
        val logsInterceptor = HttpLoggingInterceptor(HttpTimberLogger())
        logsInterceptor.level = if (BuildUtils.isRelease()) {
            HttpLoggingInterceptor.Level.HEADERS
        } else {
            HttpLoggingInterceptor.Level.BODY
        }
        return logsInterceptor
    }

    private fun provideHostResolutionInterceptor(): Interceptor {
        // val baseURLString = appPreference.getString(PreferenceProps.API_BASE_URL, "")
        // val baseURL = Uri.parse(baseURLString)
        // val host = baseURL.host
        return HostResolutionInterceptor()
    }

    @Provides
    @ApplicationScope
    fun provideRetrofit(
        appPreference: IAppPreference,
        @Named(CHECK_HTTP_CLIENT)
        httpClient: OkHttpClient
    ): Retrofit {
        val defaultBaseURL = appPreference.getString(PreferenceProps.API_BASE_URL, "Can't find a default API base URL")
        return Retrofit.Builder()
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .baseUrl(defaultBaseURL)
            .client(httpClient)
            .build()
    }

    @Provides
    @ApplicationScope
    fun provideAppUpdateCheckAPI(
        retrofit: Retrofit
    ): ISparkPointUpdateCheckApi {
        Timber.v("[Updater] Init check API")
        return retrofit.create(ISparkPointUpdateCheckApi::class.java)
    }

    @Provides
    @ApplicationScope
    fun provideHeartBeatAPI(
        retrofit: Retrofit
    ): ISparkPointHeartBeatApi {
        Timber.v("[Updater] Init heartbeat API")
        return retrofit.create(ISparkPointHeartBeatApi::class.java)
    }
}