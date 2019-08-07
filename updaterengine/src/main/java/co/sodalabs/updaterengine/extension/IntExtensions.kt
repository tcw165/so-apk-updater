package co.sodalabs.updaterengine.extension

fun Int.toMilliseconds(): Long {
    return 1000L * this
}

fun Int.mbToBytes(): Long {
    return 1024L * 1024L * this
}