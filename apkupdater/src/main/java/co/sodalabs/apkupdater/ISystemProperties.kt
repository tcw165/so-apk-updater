package co.sodalabs.apkupdater

interface ISystemProperties {
    fun getString(key: String, default: String): String
    fun getInt(key: String, default: Int): Int
    fun getLong(key: String, default: Long): Long
    fun getBoolean(key: String, default: Boolean): Boolean
}