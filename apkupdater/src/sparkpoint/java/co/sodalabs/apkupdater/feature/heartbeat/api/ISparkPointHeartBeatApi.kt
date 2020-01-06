package co.sodalabs.apkupdater.feature.heartbeat.api

import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatBody
import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatResponse
import co.sodalabs.apkupdater.feature.remoteConfig.RemoteConfig
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path

interface ISparkPointHeartBeatApi {

    @POST("hey")
    fun poke(
        @Body body: HeartBeatBody
    ): Call<HeartBeatResponse>

    @PATCH("device/{device_id}")
    fun patchRemoteConfig(
        @Path("device_id") deviceID: String,
        @Body body: RemoteConfig
    ): Call<Void>
}