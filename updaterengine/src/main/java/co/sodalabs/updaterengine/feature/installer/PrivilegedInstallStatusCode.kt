package co.sodalabs.updaterengine.feature.installer

import co.sodalabs.updaterengine.exception.InstallInconsistentCertificateException
import co.sodalabs.updaterengine.exception.InstallInvalidApkException

/**
 * Copy from AOSP source code, android.content.pm.PackageManager
 */
object PrivilegedInstallStatusCode {

    /**
     * SodaLabs proprietary error code
     */
    const val INSTALL_NO_PRIVILEGED_PERMISSIONS = 0
    /**
     * Following return codes are copied from AOSP 5.1 source code
     */
    const val INSTALL_SUCCEEDED = 1
    const val INSTALL_FAILED_ALREADY_EXISTS = -1
    const val INSTALL_FAILED_INVALID_APK = -2
    const val INSTALL_FAILED_INVALID_URI = -3
    const val INSTALL_FAILED_INSUFFICIENT_STORAGE = -4
    const val INSTALL_FAILED_DUPLICATE_PACKAGE = -5
    const val INSTALL_FAILED_NO_SHARED_USER = -6
    const val INSTALL_FAILED_UPDATE_INCOMPATIBLE = -7
    const val INSTALL_FAILED_SHARED_USER_INCOMPATIBLE = -8
    const val INSTALL_FAILED_MISSING_SHARED_LIBRARY = -9
    const val INSTALL_FAILED_REPLACE_COULDNT_DELETE = -10
    const val INSTALL_FAILED_DEXOPT = -11
    const val INSTALL_FAILED_OLDER_SDK = -12
    const val INSTALL_FAILED_CONFLICTING_PROVIDER = -13
    const val INSTALL_FAILED_NEWER_SDK = -14
    const val INSTALL_FAILED_TEST_ONLY = -15
    const val INSTALL_FAILED_CPU_ABI_INCOMPATIBLE = -16
    const val INSTALL_FAILED_MISSING_FEATURE = -17
    const val INSTALL_FAILED_CONTAINER_ERROR = -18
    const val INSTALL_FAILED_INVALID_INSTALL_LOCATION = -19
    const val INSTALL_FAILED_MEDIA_UNAVAILABLE = -20
    const val INSTALL_FAILED_VERIFICATION_TIMEOUT = -21
    const val INSTALL_FAILED_VERIFICATION_FAILURE = -22
    const val INSTALL_FAILED_PACKAGE_CHANGED = -23
    const val INSTALL_FAILED_UID_CHANGED = -24
    const val INSTALL_FAILED_VERSION_DOWNGRADE = -25
    const val INSTALL_PARSE_FAILED_NOT_APK = -100
    const val INSTALL_PARSE_FAILED_BAD_MANIFEST = -101
    const val INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION = -102
    const val INSTALL_PARSE_FAILED_NO_CERTIFICATES = -103
    const val INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES = -104
    const val INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING = -105
    const val INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME = -106
    const val INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID = -107
    const val INSTALL_PARSE_FAILED_MANIFEST_MALFORMED = -108
    const val INSTALL_PARSE_FAILED_MANIFEST_EMPTY = -109
    const val INSTALL_FAILED_INTERNAL_ERROR = -110
    const val INSTALL_FAILED_USER_RESTRICTED = -111
    const val INSTALL_FAILED_DUPLICATE_PERMISSION = -112
    const val INSTALL_FAILED_NO_MATCHING_ABIS = -113
    /**
     * Internal return code for NativeLibraryHelper methods to indicate that the package
     * being processed did not contain any native code. This is placed here only so that
     * it can belong to the same value space as the other install failure codes.
     */
    const val NO_NATIVE_LIBRARIES = -114
    const val INSTALL_FAILED_ABORTED = -115

    private val INSTALL_RETURN_CODES = mapOf(
        INSTALL_SUCCEEDED to "Success",
        INSTALL_FAILED_ALREADY_EXISTS to "Package is already installed.",
        INSTALL_FAILED_INVALID_APK to "The package archive file is invalid.",
        INSTALL_FAILED_INVALID_URI to "The URI passed in is invalid.",
        INSTALL_FAILED_INSUFFICIENT_STORAGE to "The package manager service found that the device didn't have enough storage space to install the app.",
        INSTALL_FAILED_DUPLICATE_PACKAGE to "A package is already installed with the same name.",
        INSTALL_FAILED_NO_SHARED_USER to "The requested shared user does not exist.",
        INSTALL_FAILED_UPDATE_INCOMPATIBLE to "A previously installed package of the same name has a different signature than the new package (and the old package's data was not removed).",
        INSTALL_FAILED_SHARED_USER_INCOMPATIBLE to "The new package is requested a shared user which is already installed on the device and does not have matching signature.",
        INSTALL_FAILED_MISSING_SHARED_LIBRARY to "The new package uses a shared library that is not available.",
        INSTALL_FAILED_REPLACE_COULDNT_DELETE to "Unknown",
        INSTALL_FAILED_DEXOPT to "The package failed while optimizing and validating its dex files, either because there was not enough storage or the validation failed.",
        INSTALL_FAILED_OLDER_SDK to "The new package failed because the current SDK version is older than that required by the package.",
        INSTALL_FAILED_CONFLICTING_PROVIDER to "The new package failed because it contains a content provider with the same authority as a provider already installed in the system.",
        INSTALL_FAILED_NEWER_SDK to "The new package failed because the current SDK version is newer than that required by the package.",
        INSTALL_FAILED_TEST_ONLY to "The new package failed because it has specified that it is a test-only package and the caller has not supplied the {@link #INSTALL_ALLOW_TEST} flag.",
        INSTALL_FAILED_CPU_ABI_INCOMPATIBLE to "The package being installed contains native code, but none that is compatible with the device's CPU_ABI.",
        INSTALL_FAILED_MISSING_FEATURE to "The new package uses a feature that is not available.",
        INSTALL_FAILED_CONTAINER_ERROR to "A secure container mount point couldn't be accessed on external media.",
        INSTALL_FAILED_INVALID_INSTALL_LOCATION to "The new package couldn't be installed in the specified install location.",
        INSTALL_FAILED_MEDIA_UNAVAILABLE to "The new package couldn't be installed in the specified install location because the media is not available.",
        INSTALL_FAILED_VERIFICATION_TIMEOUT to "The new package couldn't be installed because the verification timed out.",
        INSTALL_FAILED_VERIFICATION_FAILURE to "The new package couldn't be installed because the verification did not succeed.",
        INSTALL_FAILED_PACKAGE_CHANGED to "The package changed from what the calling program expected.",
        INSTALL_FAILED_UID_CHANGED to "The new package is assigned a different UID than it previously held.",
        INSTALL_FAILED_VERSION_DOWNGRADE to "The new package has an older version code than the currently installed package.",
        INSTALL_PARSE_FAILED_NOT_APK to "The parser was given a path that is not a file, or does not end with the expected '.apk' extension.",
        INSTALL_PARSE_FAILED_BAD_MANIFEST to "the parser was unable to retrieve the AndroidManifest.xml file.",
        INSTALL_PARSE_FAILED_UNEXPECTED_EXCEPTION to "The parser encountered an unexpected exception.",
        INSTALL_PARSE_FAILED_NO_CERTIFICATES to "The parser did not find any certificates in the .apk.",
        INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES to "The parser found inconsistent certificates on the files in the .apk.",
        INSTALL_PARSE_FAILED_CERTIFICATE_ENCODING to "The parser encountered a CertificateEncodingException in one of the files in the .apk.",
        INSTALL_PARSE_FAILED_BAD_PACKAGE_NAME to "The parser encountered a bad or missing package name in the manifest.",
        INSTALL_PARSE_FAILED_BAD_SHARED_USER_ID to "The parser encountered a bad shared user id name in the manifest.",
        INSTALL_PARSE_FAILED_MANIFEST_MALFORMED to "The parser encountered some structural problem in the manifest.",
        INSTALL_PARSE_FAILED_MANIFEST_EMPTY to "The parser did not find any actionable tags (instrumentation or application) in the manifest.",
        INSTALL_FAILED_INTERNAL_ERROR to "The system failed to install the package because of system issues.",
        INSTALL_FAILED_USER_RESTRICTED to "The system failed to install the package because the user is restricted from installing apps.",
        INSTALL_FAILED_DUPLICATE_PERMISSION to "The system failed to install the package because it is attempting to define a permission that is already defined by some existing package.",
        INSTALL_FAILED_NO_MATCHING_ABIS to "The system failed to install the package because its packaged native code did not match any of the ABIs supported by the system."
    )

    const val DELETE_SUCCEEDED = 1
    const val DELETE_FAILED_INTERNAL_ERROR = -1
    const val DELETE_FAILED_DEVICE_POLICY_MANAGER = -2
    const val DELETE_FAILED_USER_RESTRICTED = -3
    const val DELETE_FAILED_OWNER_BLOCKED = -4
    const val DELETE_FAILED_ABORTED = -5

    private val UNINSTALL_RETURN_CODES = mapOf(
        DELETE_SUCCEEDED to "Success",
        DELETE_FAILED_INTERNAL_ERROR to " the system failed to delete the package for an unspecified reason.",
        DELETE_FAILED_DEVICE_POLICY_MANAGER to "the system failed to delete the package because it is the active DevicePolicy manager.",
        DELETE_FAILED_USER_RESTRICTED to "the system failed to delete the package since the user is restricted.",
        DELETE_FAILED_OWNER_BLOCKED to "the system failed to delete the package because a profile or device owner has marked the package as uninstallable."
    )
}

fun Int.toError(
    filePath: String
): Throwable {
    return when (this) {
        PrivilegedInstallStatusCode.INSTALL_FAILED_INTERNAL_ERROR -> UnknownError("Cannot install $filePath")
        PrivilegedInstallStatusCode.INSTALL_FAILED_INVALID_APK -> InstallInvalidApkException(filePath)
        PrivilegedInstallStatusCode.INSTALL_PARSE_FAILED_NOT_APK -> InstallInvalidApkException(filePath)
        PrivilegedInstallStatusCode.INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES -> InstallInconsistentCertificateException(filePath)
        else -> TODO()
    }
}