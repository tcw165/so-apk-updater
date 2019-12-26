package co.sodalabs.apkupdater.feature.heartbeat.data

import androidx.annotation.Keep
import co.sodalabs.apkupdater.feature.remoteConfig.RemoteConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class HeartBeatResponse(
    @Json(name = "message")
    val message: String,
    @Json(name = "data")
    val remoteConfig: RemoteConfig
)