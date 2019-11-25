package co.sodalabs.apkupdater.feature.heartbeat.data

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class HeartBeatBody(
    @Json(name = "device_id")
    val deviceID: String,
    @Json(name = "hardware_id")
    val hardwareID: String,
    @Json(name = "firmware_version")
    val firmwareVersion: String,
    @Json(name = "apk_version")
    val sparkpointPlayerVersion: String,
    @Json(name = "updater_version")
    val updaterVersion: String,
    @Json(name = "updater_state")
    val updaterState: String,
    @Json(name = "updater_metadata")
    val updaterMetadata: Map<String, Any>
)