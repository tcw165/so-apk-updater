package co.sodalabs.privilegedinstaller

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Binder
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.Arrays

class AccessProtectionHelper(
    private val pm: PackageManager
) {

    /**
     * Checks if process that binds to this service (i.e. the package name corresponding to the
     * process) is in the whitelist.
     *
     * @return true if process is allowed to use this service
     */
    fun isCallerAllowed(): Boolean {
        return isUidAllowed(Binder.getCallingUid())
    }

    private fun isUidAllowed(uid: Int): Boolean {
        val callingPackages = pm.getPackagesForUid(uid)
            ?: throw RuntimeException("Should not happen. No packages associated to caller UID!")

        // is calling package allowed to use this service?
        // NOTE: No support for sharedUserIds
        // callingPackages contains more than one entry when sharedUserId has been used
        // No plans to support sharedUserIds due to many bugs connected to them:
        // http://java-hamster.blogspot.de/2010/05/androids-shareduserid.html
        val currentPkg = callingPackages[0]
        return isPackageAllowed(currentPkg)
    }

    private fun isPackageAllowed(packageName: String): Boolean {
        if (packageName == BuildConfig.APPLICATION_ID) {
            Timber.v("Caller (package name: \"$packageName\") is allowed to access the privileged extension!")
            return true
        }

        try {
            val currentPackageCert = getPackageCertificate(packageName)
            val digest = MessageDigest.getInstance("SHA-256")
            val packageHash = digest.digest(currentPackageCert)
            val packageHashString = BigInteger(1, packageHash).toString(16)
            Timber.v("Compare caller's package cert hash: \"$packageHashString\" with...")

            val whitelist = BuildConfig.CLIENT_WHITELIST
            for (i in 0 until whitelist.size) {
                val hash = whitelist[i]
                val whitelistHash = hash.hexStringToByteArray()

                val packageCertMatches = Arrays.equals(whitelistHash, packageHash)
                if (packageCertMatches) {
                    Timber.v("....\"$hash\" ==> matched")
                    Timber.v("Caller (package name: \"$packageName\") is allowed to access the privileged extension!")
                    return true
                } else {
                    Timber.v("....\"$hash\"")
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e.message)
        }

        Timber.v("Caller (package name: \"$packageName\") is NOT allowed to access the privileged extension!")
        return false
    }

    private fun getPackageCertificate(packageName: String): ByteArray {
        try {
            // we do check the byte array of *all* signatures
            @SuppressLint("PackageManagerGetSignatures")
            val pkgInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)

            // NOTE: Silly Android API naming: Signatures are actually certificates
            val certificates = pkgInfo.signatures
            val outputStream = ByteArrayOutputStream()

            certificates.forEach {
                outputStream.write(it.toByteArray())
            }

            // Even if an apk has several certificates, these certificates should never change
            // Google Play does not allow the introduction of new certificates into an existing apk
            // Also see this attack: http://stackoverflow.com/a/10567852
            return outputStream.toByteArray()
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e.message)
        } catch (e: IOException) {
            throw RuntimeException(e.message)
        }
    }
}