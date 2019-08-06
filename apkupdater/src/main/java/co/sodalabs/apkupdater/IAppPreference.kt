package co.sodalabs.apkupdater

import io.reactivex.Observable

interface IAppPreference {
    fun getInt(prop: String, default: Int): Int
    fun putInt(prop: String, value: Int)
    fun observeIntChange(prop: String, default: Int): Observable<Int>

    fun getBoolean(prop: String, default: Boolean): Boolean
    fun putBoolean(prop: String, value: Boolean)
    fun observeBooleanChange(prop: String, default: Boolean): Observable<Boolean>

    fun containsKey(prop: String): Boolean
    fun observeAnyChange(): Observable<Unit>
}