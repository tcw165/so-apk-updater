package co.sodalabs.apkupdater

import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject

class AndroidSystemProperties @Inject constructor() : ISystemProperties {

    private var getStringMethod: Method? = null
    private var getIntMethod: Method? = null
    private var getLongMethod: Method? = null
    private var getBooleanMethod: Method? = null

    init {
        val propClazz = Class.forName("android.os.SystemProperties")
        try {
            getStringMethod = propClazz.getDeclaredMethod("get", String::class.java, String::class.java)
        } catch (error: Throwable) {
            Timber.w(error, "Can't reflect android.os.SystemProperties.get(String)")
        }
        try {
            getIntMethod = propClazz.getDeclaredMethod("getInt", String::class.java, Int::class.java)
        } catch (error: Throwable) {
            Timber.w(error, "Can't reflect android.os.SystemProperties.getInt(int)")
        }
        try {
            getLongMethod = propClazz.getDeclaredMethod("getLong", String::class.java, Long::class.java)
        } catch (error: Throwable) {
            Timber.w(error, "Can't reflect android.os.SystemProperties.getLong(long)")
        }
        try {
            getBooleanMethod = propClazz.getDeclaredMethod("getBoolean", String::class.java, Boolean::class.java)
        } catch (error: Throwable) {
            Timber.w(error, "Can't reflect android.os.SystemProperties.getBoolean(boolean)")
        }
    }

    override fun getString(key: String, default: String): String {
        return getStringMethod?.invoke(null, key, default) as? String ?: default
    }

    override fun getInt(key: String, default: Int): Int {
        return getIntMethod?.invoke(null, key, default) as? Int ?: default
    }

    override fun getLong(key: String, default: Long): Long {
        return getLongMethod?.invoke(null, key, default) as? Long ?: default
    }

    override fun getBoolean(key: String, default: Boolean): Boolean {
        return getBooleanMethod?.invoke(null, key, default) as? Boolean ?: default
    }
}