package co.sodalabs.updaterengine

import io.reactivex.Observable

interface IAppPreference {

    var logFileCreatedTimestamp: Long

    fun getInt(prop: String, default: Int): Int
    fun putInt(prop: String, value: Int)
    fun observeIntChange(prop: String, default: Int): Observable<Int>

    fun getLong(prop: String, default: Long): Long
    fun putLong(prop: String, value: Long)
    fun observeLongChange(prop: String, default: Long): Observable<Long>

    fun getBoolean(prop: String, default: Boolean): Boolean
    fun putBoolean(prop: String, value: Boolean)
    fun observeBooleanChange(prop: String, default: Boolean): Observable<Boolean>

    fun getString(prop: String): String?
    fun getString(prop: String, default: String): String
    fun putString(prop: String, value: String)
    fun observeStringChange(prop: String, default: String): Observable<String>

    fun unsetKey(key: String)
    fun containsKey(prop: String): Boolean
    fun observeAnyChange(): Observable<String>

    fun forceFlush()
}