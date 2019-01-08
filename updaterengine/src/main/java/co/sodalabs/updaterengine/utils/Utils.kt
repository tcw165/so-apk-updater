package co.sodalabs.updaterengine.utils

import timber.log.Timber
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

object Utils {

    /**
     * Get the checksum hash of the file {@code apk} using the algorithm in {@code algo}.
     * {@code apk} must exist on the filesystem and {@code algo} must be supported
     * by this device, otherwise an {@link IllegalArgumentException} is thrown.  This
     * method must be very defensive about checking whether the file exists, since APKs
     * can be uninstalled/deleted in background at any time, even if this is in the
     * middle of running.
     * <p>
     * This also will run into filesystem corruption if the device is having trouble.
     * So hide those so F-Droid does not pop up crash reports about that. As such this
     * exception-message-parsing-and-throwing-a-new-ignorable-exception-hackery is
     * probably warranted. See https://www.gitlab.com/fdroid/fdroidclient/issues/855
     * for more detail.
     */
    fun getBinaryHash(apk: File, algo: String): String? {
        var fis: FileInputStream? = null

        try {
            val md = MessageDigest.getInstance(algo)
            fis = FileInputStream(apk)
            val bis = BufferedInputStream(fis)

            val buffer = ByteArray(8192)
            var bytes = bis.read(buffer)

            while (bytes >= 0) {
                md.update(buffer, 0, bytes)
                bytes = bis.read(buffer)
            }

            val mdbytes = md.digest()
            return mdbytes.toHex().toLowerCase(Locale.ENGLISH)
        } catch (e: IOException) {
            val message = e.message
            if (message?.contains("read failed: EIO (I/O error)") == true) {
                Timber.d("potential filesystem corruption while accessing $apk: $message")
            } else if (message?.contains(" ENOENT ") == true) {
                Timber.d("$apk vanished: $message")
            }
        } catch (e: NoSuchAlgorithmException) {
            throw IllegalArgumentException(e)
        } finally {
            closeQuietly(fis)
        }

        return null
    }

    fun closeQuietly(closeable: Closeable?) {
        if (closeable == null) {
            return
        }
        try {
            closeable.close()
        } catch (ioe: IOException) {
            // ignore
        }
    }
}