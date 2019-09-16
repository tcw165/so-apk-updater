package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.android.parcel.Parcelize
import java.io.File

@Keep
@Parcelize
@JsonClass(generateAdapter = true)
data class DownloadedAppUpdate(
    @Json(name = "file")
    val file: File,
    @Json(name = "from_update")
    val fromUpdate: AppUpdate
    // TODO: Add timestamp?
) : Parcelable