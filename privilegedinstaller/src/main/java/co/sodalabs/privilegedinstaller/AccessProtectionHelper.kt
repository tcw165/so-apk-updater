package co.sodalabs.privilegedinstaller

import android.annotation.SuppressLint
import android.content.Context
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
    context: Context,
    private val whitelist: Set<Pair<String, String>> = ClientWhitelist.whitelist
) {

    private val pm = context.packageManager

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
        Timber.d("Checking if package is allowed to access privileged extension: $packageName")

        if (packageName == BuildConfig.APPLICATION_ID) {
            Timber.v("Package $packageName is allowed to access the privileged extension!")
            return true
        }

        try {
            val currentPackageCert = getPackageCertificate(packageName)
            val digest = MessageDigest.getInstance("SHA-256")
            val packageHash = digest.digest(currentPackageCert)

            whitelist.forEach { (whitelistPackageName, whitelistHashString) ->
                val whitelistHash = whitelistHashString.hexStringToByteArray()
                val packageHashString = BigInteger(1, packageHash).toString(16)

                Timber.d("Allowed cert hash: $whitelistHashString")
                Timber.d("Package cert hash: $packageHashString")

                val packageNameMatches = packageName == whitelistPackageName
                val packageCertMatches = Arrays.equals(whitelistHash, packageHash)
                if (packageNameMatches && packageCertMatches) {
                    Timber.d("Package is allowed to access the privileged extension!")
                    return true
                }
            }
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException(e.message)
        }

        Timber.e("Package is NOT allowed to access the privileged extension!")
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