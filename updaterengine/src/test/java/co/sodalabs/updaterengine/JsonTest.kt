package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.data.AppUpdate
import co.sodalabs.updaterengine.data.DownloadedUpdate
import co.sodalabs.updaterengine.jsonadapter.FileAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Test
import java.io.File

class JsonTest {

    @Test
    fun `serialize downloaded update`() {
        val data = DownloadedUpdate(
            file = File("/tmp/test.apk"),
            fromUpdate = AppUpdate(
                packageName = "co.sodalabs.test",
                versionName = "1.0.0",
                versionCode = 1000000,
                downloadUrl = "http://co.sodalabs.blob/test.apk",
                hash = "hash"
            )
        )

        val jsonBuilder = Moshi.Builder()
            .add(FileAdapter())
            .build()
        val adapter = jsonBuilder.adapter(DownloadedUpdate::class.java)
        val jsonText = adapter.toJson(data)

        println(jsonText)
        assert(jsonText == "{\"file\":\"/tmp/test.apk\",\"from_update\":{\"app_id\":\"co.sodalabs.test\",\"download_url\":\"http://co.sodalabs.blob/test.apk\",\"file_hash\":\"hash\",\"version_name\":\"1.0.0\",\"version_code\":1000000}}")
    }

    @Test
    fun `serialize a list of downloaded update`() {
        val unit = DownloadedUpdate(
            file = File("/tmp/test.apk"),
            fromUpdate = AppUpdate(
                packageName = "co.sodalabs.test",
                versionName = "1.0.0",
                versionCode = 1000000,
                downloadUrl = "http://co.sodalabs.blob/test.apk",
                hash = "hash"
            )
        )
        val data = listOf(unit, unit)

        val jsonBuilder = Moshi.Builder()
            .add(FileAdapter())
            .build()
        val listType = Types.newParameterizedType(List::class.java, DownloadedUpdate::class.java)
        val adapter = jsonBuilder.adapter<List<DownloadedUpdate>>(listType)
        val jsonText = adapter.toJson(data)

        println(jsonText)
        assert(
            jsonText == "[{\"file\":\"/tmp/test.apk\",\"from_update\":{\"app_id\":\"co.sodalabs.test\",\"download_url\":\"http://co.sodalabs.blob/test.apk\",\"file_hash\":\"hash\",\"version_name\":\"1.0.0\",\"version_code\":1000000}},{\"file\":\"/tmp/test.apk\",\"from_update\":{\"app_id\":\"co.sodalabs.test\",\"download_url\":\"http://co.sodalabs.blob/test.apk\",\"file_hash\":\"hash\",\"version_name\":\"1.0.0\",\"version_code\":1000000}}]")
    }

    @Test
    fun `deserialize downloaded update`() {
        val jsonText = "{\"file\":\"/tmp/test.apk\",\"from_update\":{\"app_id\":\"co.sodalabs.test\",\"download_url\":\"http://co.sodalabs.blob/test.apk\",\"file_hash\":\"hash\",\"version_name\":\"1.0.0\",\"version_code\":1000000}}"

        val jsonBuilder = Moshi.Builder()
            .add(FileAdapter())
            .build()
        val adapter = jsonBuilder.adapter(DownloadedUpdate::class.java)
        val data = adapter.fromJson(jsonText) ?: throw NullPointerException()

        println(data)
        assert(data.file == File("/tmp/test.apk"))
    }
}