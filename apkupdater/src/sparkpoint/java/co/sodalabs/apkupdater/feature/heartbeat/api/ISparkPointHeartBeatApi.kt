package co.sodalabs.apkupdater.feature.heartbeat.api

import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatBody
import co.sodalabs.apkupdater.feature.heartbeat.data.HeartBeatResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.POST

interface ISparkPointHeartBeatApi {

    @POST("hey")
    fun poke(
        @Body body: HeartBeatBody
    ): Call<HeartBeatResponse>
}