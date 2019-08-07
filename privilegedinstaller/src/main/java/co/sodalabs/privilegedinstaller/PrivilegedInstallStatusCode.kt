package co.sodalabs.privilegedinstaller

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