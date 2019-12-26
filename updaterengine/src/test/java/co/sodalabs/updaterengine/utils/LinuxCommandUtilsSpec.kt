package co.sodalabs.updaterengine.utils

import io.kotlintest.matchers.collections.shouldNotBeEmpty
import io.kotlintest.matchers.string.shouldContain
import io.kotlintest.specs.BehaviorSpec
import io.mockk.every
import io.mockk.spyk
import org.amshove.kluent.shouldBeIn
import org.amshove.kluent.shouldNotBeNullOrEmpty

// A sample from actual logs containing disk usage stats
private val DISK_USAGE_LOGS =
    """
        /dev                   937.6M   128.0K   937.5M   4096
        /mnt/asec              937.6M     0.0K   937.6M   4096
        /system                  2.0G   708.5M     1.3G   4096
        /data                    8.9G     1.1G     7.8G   4096
        /storage/emulated      937.6M     0.0K   937.6M   4096
        /storage/emulated/legacy     8.9G     1.1G     7.8G   4096
    """.trimIndent()

private val STORAGE_UNITS = arrayOf('G', 'M', 'K')

class LinuxCommandUtilsSpec : BehaviorSpec({

    Given("a Linux command execution utility") {
        val linuxUtil = spyk(LinuxCommandUtils())

        When("disk usage specs are fetched") {
            every { linuxUtil.logDiskUsage() } returns DISK_USAGE_LOGS

            val diskUsageStats = linuxUtil.collectDiskUsageLogs()

            Then("stats should not be empty") {
                diskUsageStats.shouldNotBeEmpty()
            }
            Then("stats should include name") {
                diskUsageStats.first().name.let { s ->
                    s.shouldNotBeNullOrEmpty()
                    s.shouldContain("/")
                }
            }
            Then("stats should include total size") {
                diskUsageStats.first().total.let { s ->
                    s.shouldNotBeNullOrEmpty()
                    s.last().shouldBeIn(STORAGE_UNITS)
                }
            }
            Then("stats should include used space") {
                diskUsageStats.first().used.let { s ->
                    s.shouldNotBeNullOrEmpty()
                    s.last().shouldBeIn(STORAGE_UNITS)
                }
            }
            Then("stats should include free space") {
                diskUsageStats.first().free.let { s ->
                    s.shouldNotBeNullOrEmpty()
                    s.last().shouldBeIn(STORAGE_UNITS)
                }
            }
            Then("stats should include block size") {
                diskUsageStats.first().blockSize.shouldNotBeNullOrEmpty()
            }
        }
    }
})