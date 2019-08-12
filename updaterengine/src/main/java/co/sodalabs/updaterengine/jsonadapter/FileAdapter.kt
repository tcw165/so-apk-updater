package co.sodalabs.updaterengine.jsonadapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson
import java.io.File

class FileAdapter : JsonAdapter<File>() {

    @FromJson
    override fun fromJson(reader: JsonReader): File? {
        val fileString = reader.nextString()
        return try {
            File(fileString)
        } catch (ignored: Throwable) {
            null
        }
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: File?) {
        value?.let { safeValue ->
            writer.value(safeValue.absolutePath)
        }
    }
}