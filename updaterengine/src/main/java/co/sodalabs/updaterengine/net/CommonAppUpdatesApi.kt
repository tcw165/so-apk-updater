package co.sodalabs.updaterengine.net

import co.sodalabs.updaterengine.data.AppUpdate
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path

interface CommonAppUpdatesApi {

    @GET("{package_name}")
    fun getAppUpdate(
        @Path("package_name") packageName: String
    ): Call<AppUpdate>
}