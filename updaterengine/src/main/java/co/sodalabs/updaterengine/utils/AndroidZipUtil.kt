package co.sodalabs.updaterengine.utils

import android.content.res.AssetManager
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IZipUtil
import co.sodalabs.updaterengine.exception.AssetNotFoundException
import co.sodalabs.updaterengine.exception.UnzippingException
import io.reactivex.Single
import io.reactivex.SingleEmitter
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import javax.inject.Inject

private const val ONE_KB = 1024
private const val EIGHT_KB = 8 * ONE_KB
private const val ONE_MB = ONE_KB.shl(10)
private const val TEN_MB = 10 * ONE_MB

class AndroidZipUtil @Inject constructor(
    private val assetManager: AssetManager,
    private val schedulers: IThreadSchedulers
) : IZipUtil {

    override fun extractZipAsset(
        assetZipFilePath: String,
        dstDir: File
    ): Single<File> {
        return copyZipToDestineDir(assetZipFilePath, dstDir)
            .flatMap { zipFile -> actuallyUnzip(zipFile, dstDir) }
    }

    private fun copyZipToDestineDir(
        assetZipFilePath: String,
        dstDir: File
    ): Single<File> {
        return Single
            .create<File> { emitter ->
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null

                emitter.setCancellable {
                    // No-op
                }

                try {
                    Timber.v("[ZipUtil] Copy the $assetZipFilePath from assets to $dstDir dir")
                    // Prepare input stream.
                    inputStream = BufferedInputStream(getAssetFile(assetZipFilePath))
                    // Prepare output stream.
                    val outFile = File(dstDir, assetZipFilePath).apply {
                        if (!parentFile.exists()) {
                            parentFile.mkdirs()
                        }
                        createNewFile()
                    }
                    outputStream = BufferedOutputStream(outFile.outputStream())

                    val buffer = ByteArray(EIGHT_KB)
                    // The checkpoint for logging
                    var checkpoint = 0
                    var read = inputStream.read(buffer)
                    var totalRead = read
                    while (read > 0) {
                        outputStream.write(buffer)
                        outputStream.flush()

                        if (totalRead / TEN_MB != checkpoint) {
                            checkpoint = totalRead / TEN_MB
                            Timber.v("[ZipUtil] Copying the $assetZipFilePath... ${totalRead.toFloat() / ONE_MB} MB copied")
                        }
                        if (emitter.isDisposed) break
                        read = inputStream.read(buffer)
                        totalRead += read
                    }

                    // Signal value to move on
                    if (!emitter.isDisposed) {
                        emitter.onSuccess(outFile)
                    }
                } catch (error: Throwable) {
                    if (!emitter.isDisposed) {
                        val translatedError = when (error) {
                            // Convert IoException to our exception!
                            is IOException -> UnzippingException("Failed to copy $assetZipFilePath from assets to $dstDir dir")
                            else -> error
                        }
                        emitter.onError(translatedError)
                    }
                } finally {
                    inputStream?.close()
                    outputStream?.close()
                }
            }
            .subscribeOn(schedulers.io())
    }

    /**
     * The helper function gets the [InputStream] from assets and also translates
     * any [IOException] to more verbal exception.
     * Note: [SocketTimeoutException] is also an [IOException].
     */
    private fun getAssetFile(
        assetZipFilePath: String
    ): InputStream {
        return try {
            assetManager.open(assetZipFilePath)
        } catch (error: Throwable) {
            // Convert IoException to our exception!
            throw AssetNotFoundException("Can't find $assetZipFilePath in assets")
        }
    }

    private fun actuallyUnzip(
        zip: File,
        dstDirPath: File
    ): Single<File> {
        return Single
            .create<File> { emitter ->
                var zipFile: ZipFile? = null
                var zipEntryStream: ZipInputStream? = null

                emitter.setCancellable {
                    // No-op
                }

                Timber.v("[ZipUtil] Unzip the $zip from assets to cache dir")
                try {
                    zipFile = ZipFile(zip)
                    zipEntryStream = ZipInputStream(zip.inputStream())

                    var entry: ZipEntry? = zipEntryStream.nextEntry
                    while (entry != null) {
                        val dstFile = File(dstDirPath, entry.name)
                        if (entry.isDirectory) {
                            Timber.v("[ZipUtil] Create dir, $dstFile")
                            dstFile.mkdirs()
                        } else {
                            extractZipEntry(zipFile, entry, dstFile, emitter)
                        }

                        if (emitter.isDisposed) break
                        entry = zipEntryStream.nextEntry
                    }

                    if (emitter.isDisposed) {
                        Timber.v("[ZipUtil] Got canceled so we'll clean up all the temporary files")
                        if (dstDirPath.exists()) {
                            dstDirPath.deleteRecursively()
                        }
                    }

                    // Remove ZIP file
                    if (zip.exists()) {
                        Timber.v("[ZipUtil] Successfully unzip $zip and now delete the file!")
                        zip.deleteRecursively()
                    }
                } catch (error: Throwable) {
                    if (!emitter.isDisposed) {
                        val translatedError = when (error) {
                            // Convert IoException to our exception!
                            is IOException -> UnzippingException(error.toString())
                            else -> error
                        }
                        emitter.onError(translatedError)
                    }
                } finally {
                    zipEntryStream?.close()
                    zipFile?.close()
                }

                if (!emitter.isDisposed) {
                    emitter.onSuccess(zip)
                }
            }
            .subscribeOn(schedulers.io())
    }

    private fun extractZipEntry(
        zipFile: ZipFile,
        zipEntry: ZipEntry,
        dstFile: File,
        emitter: SingleEmitter<File>
    ) {
        var inputStream: InputStream? = null
        var outputStream: OutputStream? = null
        try {
            inputStream = zipFile.getInputStream(zipEntry)

            // Make sure all folders in the path exist
            val parent = dstFile.parentFile
            if (!parent.exists() && !parent.mkdirs()) {
                throw IOException("Couldn't create dir: $parent")
            }

            dstFile.createNewFile()
            outputStream = FileOutputStream(dstFile)
            val buffer = ByteArray(EIGHT_KB)
            // The checkpoint for logging
            var checkpoint = 0
            var read: Int = inputStream.read(buffer)
            var totalRead = read
            while (read > 0) {
                if (totalRead / TEN_MB != checkpoint) {
                    checkpoint = totalRead / TEN_MB
                    Timber.v("[ZipUtil] Unzipping entry, $zipEntry... read ${totalRead.toFloat() / ONE_MB} MB")
                }
                outputStream.write(buffer, 0, read)

                if (emitter.isDisposed) break
                read = inputStream.read(buffer)
                totalRead += read
            }
        } finally {
            inputStream?.close()
            outputStream?.close()
        }
    }
}