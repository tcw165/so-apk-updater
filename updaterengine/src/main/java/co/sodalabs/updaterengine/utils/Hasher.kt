package co.sodalabs.updaterengine.utils

import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Locale

class Hasher(
    private val type: String,
    private val file: File?,
    private val array: ByteArray?
) {

    companion object {

        /**
         * Checks the file against the provided hash, returning whether it is a match.
         */
        fun isFileMatchingHash(file: File, hash: String): Boolean {
            // TODO: Implement hash check when server supports it
            return true

            //            if (!file.exists()) {
            //                return false
            //            }
            //            try {
            //                val hasher = Hasher(hashType, file, null)
            //                return hasher.match(hash)
            //            } catch (e: NoSuchAlgorithmException) {
            //                throw RuntimeException(e)
            //            }
        }
    }

    private val digest: MessageDigest
    private var hashCache: String? = null

    init {
        try {
            digest = MessageDigest.getInstance(type)
        } catch (e: Exception) {
            throw NoSuchAlgorithmException(e)
        }
    }

    // Calculate hash (as lowercase hexadecimal string) for the file
    // specified in the constructor. This will return a cached value
    // on subsequent invocations. Returns the empty string on failure.
    private fun getHash(): String? {
        hashCache?.let { return it }

        if (file != null) {
            val buffer = ByteArray(1024)

            var input: InputStream? = null
            try {
                input = BufferedInputStream(FileInputStream(file))

                var read = input.read(buffer)
                while (read > 0) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            } catch (e: Exception) {
                this.hashCache = ""
                return this.hashCache
            } finally {
                Utils.closeQuietly(input)
            }
        } else {
            digest.update(array)
        }

        this.hashCache = digest.digest().toHex()
        return this.hashCache
    }

    // Compare the calculated hash to another string, ignoring case,
    // returning true if they are equal. The empty string and null are
    // considered non-matching.
    fun match(otherHash: String?): Boolean {
        if (otherHash == null) {
            return false
        }

        if (hashCache == null) {
            getHash()
        }
        return hashCache.equals(otherHash.toLowerCase(Locale.ENGLISH))
    }
}