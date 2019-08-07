package co.sodalabs.updaterengine.net

import android.content.Context
import android.net.Uri
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.data.SanitizedFile
import co.sodalabs.updaterengine.utils.Hasher
import co.sodalabs.updaterengine.utils.StorageUtils
import org.apache.commons.io.FileUtils
import timber.log.Timber
import java.io.File
import java.io.IOException

@Deprecated("Replaced by DiskLruCache")
object ApkCache {

    private const val CACHE_DIR = "apks"

    /**
     * Copy the APK to the safe location inside of the protected area
     * of the app to prevent attacks based on other apps swapping the file
     * out during the install process. Most likely, apkFile was just downloaded,
     * so it should still be in the RAM disk cache.
     */
    @Throws(IOException::class)
    fun copyApkFromCacheToFiles(
        context: Context,
        apkFile: File,
        expectedDownloadedUpdate: DownloadedUpdate
    ): SanitizedFile {
        val name = expectedDownloadedUpdate.fromUpdate.packageName
        val apkFileName = name + "-" + expectedDownloadedUpdate.fromUpdate.versionName + ".apk"
        return copyApkToFiles(context, apkFile, apkFileName, true, expectedDownloadedUpdate.fromUpdate.hash ?: "")
    }

    /**
     * Copy an APK from {@param apkFile} to our internal files directory for 20 minutes.
     *
     * @param verifyHash If the file was just downloaded, then you should mark this as true and
     *                   request the file to be verified once it has finished copying. Otherwise,
     *                   if the app was installed from part of the system where it can't be tampered
     *                   install (e.g. installed apks on disk) then
     */
    @Throws(IOException::class)
    fun copyApkToFiles(
        context: Context,
        apkFile: File,
        destinationName: String,
        verifyHash: Boolean,
        hash: String
    ): SanitizedFile {
        val sanitizedApkFile = SanitizedFile(context.filesDir, destinationName)

        // Don't think this is necessary, but the docs for FileUtils#copyFile() are not clear
        // on whether it overwrites destination files (pretty confident it does, as per the docs
        // in FileUtils#copyFileToDirectory() - which delegates to copyFile()).
        if (sanitizedApkFile.exists()) {
            sanitizedApkFile.delete()
        }

        Timber.d("copyApkToFiles: ${apkFile.path} -> ${sanitizedApkFile.path}")
        FileUtils.copyFile(apkFile, sanitizedApkFile)

        // verify copied file's hash install expected hash from DownloadedUpdate class
        if (verifyHash && !Hasher.isFileMatchingHash(sanitizedApkFile, hash)) {
            FileUtils.deleteQuietly(apkFile)
            throw IOException("$apkFile failed to verify!")
        }

        // 20 minutes the start of the install process, delete the file
        val apkToDelete = sanitizedApkFile
        object : Thread() {
            override fun run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
                try {
                    Thread.sleep(1200000)
                } catch (ignored: InterruptedException) {
                } finally {
                    FileUtils.deleteQuietly(apkToDelete)
                }
            }
        }.start()

        return sanitizedApkFile
    }

    /**
     * Get the full path for where an APK URL will be downloaded into.
     */
    fun getApkDownloadPath(context: Context, uri: Uri): SanitizedFile {
        val host = uri.host ?: throw IllegalArgumentException("uri does not contain host")
        val dir = File(getApkCacheDir(context), host + "-" + uri.port)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        val fileName = uri.lastPathSegment ?: throw IllegalStateException("Missing last path segment for uri")
        return SanitizedFile(dir, fileName)
    }

    /**
     * This location is only for caching, do not install directly from this location
     * because if the file is on the External Storage, any other app could swap out
     * the APK while the install was in process, allowing malware to install things.
     * Using [Installer.installPackage]
     * is fine since that does the right thing.
     */
    fun getApkCacheDir(context: Context): File {
        val apkCacheDir = File(StorageUtils.getCacheDirectory(context, true), CACHE_DIR)
        if (apkCacheDir.isFile) {
            apkCacheDir.delete()
        }
        if (!apkCacheDir.exists()) {
            apkCacheDir.mkdir()
        }
        return apkCacheDir
    }
}