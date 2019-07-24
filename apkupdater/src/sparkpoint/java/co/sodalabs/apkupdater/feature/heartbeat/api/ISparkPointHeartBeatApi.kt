package co.sodalabs.apkupdater.feature.heartbeat.api

import co.sodalabs.updaterengine.data.HeartBeatResponse
import retrofit2.Call
import retrofit2.http.PATCH
import retrofit2.http.Path

interface ISparkPointHeartBeatApi {

    @PATCH("hey/{device_id}")
    fun poke(
        @Path("device_id") deviceID: String
    ): Call<HeartBeatResponse>
}