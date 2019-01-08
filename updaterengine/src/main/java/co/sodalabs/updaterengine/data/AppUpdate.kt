package co.sodalabs.updaterengine.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AppUpdate(
    @Json(name = "_attachments")
    val attachments: String,
    @Json(name = "_etag")
    val etag: String,
    @Json(name = "_rid")
    val rid: String,
    @Json(name = "_self")
    val self: String,
    @Json(name = "_ts")
    val ts: Int,
    @Json(name = "app_id")
    val packageName: String,
    @Json(name = "download_url")
    val downloadUrl: String,
    @Json(name = "file_name")
    val fileName: String,
    @Json(name = "hash")
    val hash: String? = null,
    @Json(name = "id")
    val id: String,
    @Json(name = "tag")
    val tag: String,
    @Json(name = "update_message")
    val updateMessage: String,
    @Json(name = "version")
    val versionName: String,
    @Json(name = "version_code")
    val versionCode: Int
)