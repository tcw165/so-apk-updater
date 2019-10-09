package co.sodalabs.updaterengine

interface IPackageVersionProvider {
    fun getPackageVersion(packageName: String): String
}