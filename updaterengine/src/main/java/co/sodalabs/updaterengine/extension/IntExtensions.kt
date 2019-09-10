package co.sodalabs.updaterengine.extension

fun Int.mbToBytes(): Long {
    return 1024L * 1024L * this
}

fun Int.toBoolean(): Boolean {
    return this != 0
}