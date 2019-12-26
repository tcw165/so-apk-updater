package co.sodalabs.updaterengine.utils

data class AppDir(val path: String, val size: Long) {

    override fun toString(): String {
        return "$size\t\t$path"
    }

    companion object {
        fun fromString(s: String): AppDir {
            val (size, path) = s.split("\t").filter { it.isNotEmpty() }
            return AppDir(
                path = path,
                size = size.toLong()
            )
        }
    }
}