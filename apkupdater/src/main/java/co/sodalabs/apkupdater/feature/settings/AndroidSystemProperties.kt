package co.sodalabs.apkupdater.feature.settings

import co.sodalabs.apkupdater.ISystemProperties
import timber.log.Timber
import java.lang.reflect.Method

class AndroidSystemProperties : ISystemProperties {

    private var getStringMethod: Method? = null
    private var getIntMethod: Method? = null
    private var getLongMethod: Method? = null
    private var getBooleanMethod: Method? = null

    init {
        try {
            val propClazz = Class.forName("android.os.SystemProperties")
            getStringMethod = propClazz.getDeclaredMethod("get", String::class.java, String::class.java)
            getIntMethod = propClazz.getDeclaredMethod("get", String::class.java, Integer::class.java)
            getLongMethod = propClazz.getDeclaredMethod("get", String::class.java, Long::class.java)
            getBooleanMethod = propClazz.getDeclaredMethod("get", String::class.java, Boolean::class.java)
        } catch (error: Throwable) {
            Timber.w(error)
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