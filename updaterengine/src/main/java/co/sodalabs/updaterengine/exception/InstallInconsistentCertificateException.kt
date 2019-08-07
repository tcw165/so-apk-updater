package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class InstallInconsistentCertificateException(
    val filePath: String
) : RuntimeException("$filePath is signed with a different certificate than the one installed in the system")