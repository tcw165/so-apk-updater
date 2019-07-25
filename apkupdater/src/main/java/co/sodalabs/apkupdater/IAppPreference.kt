package co.sodalabs.apkupdater

import io.reactivex.Observable

interface IAppPreference {
    fun getInt(prop: String, default: Int): Int
    fun putInt(prop: String, value: Int)
    fun observeIntChange(prop: String, default: Int): Observable<Int>
}