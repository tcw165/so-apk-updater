package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize

@Keep
@Parcelize
@JsonClass(generateAdapter = true)
data class AppUpdate(
    @Json(name = "app_id")
    val packageName: String,
    @Json(name = "download_url")
    val downloadUrl: String,
    @Json(name = "file_hash")
    val hash: String? = null,
    @Json(name = "version_name")
    val versionName: String,
    @Deprecated("Updater engine ignores this field")
    @Json(name = "version_code")
    val versionCode: Int
) : Parcelable