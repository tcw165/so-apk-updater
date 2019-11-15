package co.sodalabs.apkupdater.feature.logpersistence.api

import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@Keep
@JsonClass(generateAdapter = true)
data class SendLogResponse(

    @Json(name = "error")
    val error: String? = null,

    @Json(name = "message")
    val message: String? = null,

    @Json(name = "statusCode")
    val statusCode: Int? = null,

    @Json(name = "status")
    val status: String? = null
)