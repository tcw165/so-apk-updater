package co.sodalabs.apkupdater

interface IPackageVersionProvider {
    fun getPackageVersion(packageName: String): String
}