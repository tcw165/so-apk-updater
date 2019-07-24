package co.sodalabs.apkupdater.feature.checker.api

import co.sodalabs.updaterengine.data.AppUpdate
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface ISparkPointUpdateCheckApi {

    @GET("apks/{package_name}")
    fun getAppUpdate(
        @Path("package_name") packageName: String
    ): Call<AppUpdate>
}