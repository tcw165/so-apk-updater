package co.sodalabs.updaterengine.extension

inline fun benchmark(runnable: () -> Unit): Long {
    val before = System.currentTimeMillis()
    runnable.invoke()
    val after = System.currentTimeMillis()
    return after - before
}