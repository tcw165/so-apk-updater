package co.sodalabs.updaterengine

interface IPackageVersionProvider {
    /**
     * Return the version name string or empty string, "", if not found.
     */
    fun getPackageVersion(packageName: String): String
}