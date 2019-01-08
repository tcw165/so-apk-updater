package co.sodalabs.updaterengine.net

import co.sodalabs.updaterengine.data.AppUpdate
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Url

interface UpdaterApi {

    @GET
    fun getAppUpdate(@Url updateUrl: String): Call<AppUpdate>
}