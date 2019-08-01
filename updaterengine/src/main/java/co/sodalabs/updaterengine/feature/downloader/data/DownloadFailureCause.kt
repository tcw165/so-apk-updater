package co.sodalabs.updaterengine.feature.downloader.data

import android.net.Uri
import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

@Parcelize
data class DownloadFailureCause(
    val packageName: String,
    val downloadURI: Uri,
    val errorCode: Int
) : Parcelable