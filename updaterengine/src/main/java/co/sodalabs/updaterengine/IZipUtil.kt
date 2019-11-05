package co.sodalabs.updaterengine

import io.reactivex.Single
import java.io.File

interface IZipUtil {
    /**
     * @return The folder path.
     */
    fun extractZipAsset(
        assetZipFilePath: String,
        dstDirPath: File
    ): Single<File>
}