package co.sodalabs.updaterengine.extension

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun getPrettyDateNow(): String {
    val dateFormat = SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
    val cal = Calendar.getInstance()
    return dateFormat.format(cal.time)
}