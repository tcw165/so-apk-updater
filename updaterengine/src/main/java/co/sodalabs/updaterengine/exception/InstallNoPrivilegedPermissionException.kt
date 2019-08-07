package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class InstallNoPrivilegedPermissionException : RuntimeException("The privileged installer doesn't have the privileged permissions")