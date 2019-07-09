@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.BuildConfig
import co.sodalabs.updaterengine.net.CommonAppUpdatesApi
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

private const val HTTP_READ_WRITE_TIMEOUT = 25L
private const val HTTP_CONNECT_TIMEOUT = 15L

// TODO: It's yet used, but we'll eventually modularize the code and use this
// TODO: module
@Module
class ApiModule constructor(
    private val context: Context
) {

    private val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor()
        logging.level = if (BuildConfig.DEBUG) {
            HttpLoggingInterceptor.Level.BODY
        } else {
            HttpLoggingInterceptor.Level.NONE
        }

        OkHttpClient.Builder()
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
    fun getAppUpdatesAPI(): CommonAppUpdatesApi = retrofit.create(CommonAppUpdatesApi::class.java)
}
