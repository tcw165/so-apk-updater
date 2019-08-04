package co.sodalabs.updaterengine.data

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.android.parcel.Parcelize
import java.io.File

@Keep
@Parcelize
data class DownloadedUpdate(
    val file: File,
    val fromUpdate: AppUpdate
) : Parcelable