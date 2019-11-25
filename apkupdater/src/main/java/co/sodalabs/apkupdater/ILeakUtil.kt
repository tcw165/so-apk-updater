package co.sodalabs.apkupdater

interface ILeakUtil {
    /**
     * The system TextLine has a memory leak issue under 23.
     *
     * Reference:
     * - https://stackoverflow.com/questions/30397356/android-memory-leak-on-textview-leakcanary-leak-can-be-ignored
     * - https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/text/TextLine.java
     */
    fun clearTextLineCache()
}