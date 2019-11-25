package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
class NoUpdateFoundException : RuntimeException("No updates found")