package co.sodalabs.apkupdater.utils

import android.annotation.SuppressLint
import co.sodalabs.apkupdater.ILeakUtil
import timber.log.Timber
import java.lang.reflect.Field
import javax.inject.Inject

class AndroidLeakUtil @Inject constructor() : ILeakUtil {

    private val lock = Any()

    @Volatile
    private var TEXT_LINE_CACHED: Field? = null

    @Suppress("UNCHECKED_CAST")
    @SuppressLint("PrivateApi")
    override fun clearTextLineCache() {
        synchronized(lock) {
            Timber.d("[LeakUtil] Cleaning the TextLine cache...")
            if (TEXT_LINE_CACHED == null) {
                try {
                    val textLineCached: Field = Class.forName("android.text.TextLine").getDeclaredField("sCached")
                    textLineCached.isAccessible = true
                    TEXT_LINE_CACHED = textLineCached
                } catch (error: Throwable) {
                    Timber.w(error)
                }
            }

            val safeField = TEXT_LINE_CACHED ?: return
            try {
                // Get reference to the TextLine sCached array.
                val cached: Array<Any?> = safeField.get(null) as Array<Any?>
                // Clear the array.
                for (i in cached.indices) {
                    cached[i] = null
                }
            } catch (error: Exception) {
                Timber.w(error)
            }
        }
    }
}