package co.sodalabs.updaterengine.exception

import androidx.annotation.Keep

@Keep
data class CompositeException(
    val errors: List<Throwable>
) : RuntimeException("This is a composition of ${errors.size} errors")