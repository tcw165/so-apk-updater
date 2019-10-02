package co.sodalabs.apkupdater

import co.sodalabs.apkupdater.data.SystemProps
import timber.log.Timber
import java.lang.reflect.Method
import javax.inject.Inject

private const val EMPTY_STRING = ""

class AndroidSystemProperties @Inject constructor(
    private val appPreference: IAppPreference
) : ISystemProperties {

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

    override fun getFirmwareVersion(): String {
        val debugVersion = appPreference.getString(PreferenceProps.MOCK_FIRMWARE_VERSION, EMPTY_STRING)
        return if (debugVersion.isNotBlank()) {
            debugVersion
        } else {
            // Actual value from system.
            getString(SystemProps.FIRMWARE_VERSION_INCREMENTAL, EMPTY_STRING)
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