package co.sodalabs.apkupdater.feature.logpersistence.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ILogSenderApi {
    @Multipart
    @POST("log")
    fun sendLogs(
        @Part("device_id") id: RequestBody,
        @Part file: MultipartBody.Part
    ): Call<SendLogResponse>
}