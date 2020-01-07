package co.sodalabs.updaterengine

import io.reactivex.Observable

interface IPackageVersionProvider {
    /**
     * Return the version name string or empty string, "", if not found.
     */
    fun getPackageVersion(packageName: String): String

    fun observePackageChanges(): Observable<Unit>
}