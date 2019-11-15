package co.sodalabs.updaterengine

import co.sodalabs.updaterengine.utils.FileUtils
import io.kotlintest.specs.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.amshove.kluent.shouldBeFalse
import org.amshove.kluent.shouldBeTrue
import org.amshove.kluent.shouldEqualTo
import org.threeten.bp.ZonedDateTime
import java.io.File

class FileUtilsSpec : BehaviorSpec({

    Given("a file utils object") {
        val fileUtils = spyk<FileUtils>()

        When("when file length is 100 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 100

            Then("should return 0 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(0)
            }
        }

        When("when file length is 1000 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 1000

            Then("should return 0 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(0)
            }
        }

        When("when file length is 10000 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 10000

            Then("should return 0 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(0)
            }
        }

        When("when file length is 100000 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 100000

            Then("should return 0 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(0)
            }
        }

        When("when file length is 500000000 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 500000000

            Then("should return 476 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(476)
            }
        }

        When("when file length is 1000000 bytes") {
            val mockFile = mockk<File>()
            every { mockFile.length() } returns 1000000

            Then("should return 0 for size in MB") {
                val sizeInMb = fileUtils.sizeInMb(mockFile)
                sizeInMb.shouldEqualTo(0)
            }
        }

        When("when file size is bigger than max size") {
            val MAX_SIZE = 50
            val mockFile = mockk<File>()
            every { fileUtils.sizeInMb(mockFile) } returns 100

            Then("should return true for size exceeded") {
                val isExceedSize = fileUtils.isExceedSize(mockFile, MAX_SIZE)
                isExceedSize.shouldBeTrue()
            }
        }

        When("when file size is smaller than max size") {
            val MAX_SIZE = 50
            val mockFile = mockk<File>()
            every { fileUtils.sizeInMb(mockFile) } returns 10

            Then("should return false for size exceeded") {
                val isExceedSize = fileUtils.isExceedSize(mockFile, MAX_SIZE)
                isExceedSize.shouldBeFalse()
            }
        }

        When("when file size is equal to max size") {
            val MAX_SIZE = 50
            val mockFile = mockk<File>()
            every { fileUtils.sizeInMb(mockFile) } returns 50

            Then("should return false for size exceeded") {
                val isExceedSize = fileUtils.isExceedSize(mockFile, MAX_SIZE)
                isExceedSize.shouldBeFalse()
            }
        }

        When("when file is created on the same day") {
            val EXPIRY_DAYS = 7L
            val createdOn = ZonedDateTime.now().toInstant().toEpochMilli()

            Then("should return false for expiry") {
                val isExpired = fileUtils.isOlderThanDuration(createdOn, EXPIRY_DAYS)
                isExpired.shouldBeFalse()
            }
        }

        When("when within expiry date") {
            val EXPIRY_DAYS = 7L
            val createdOn = ZonedDateTime.now().minusDays(2).toInstant().toEpochMilli()

            Then("should return false for expiry") {
                val isExpired = fileUtils.isOlderThanDuration(createdOn, EXPIRY_DAYS)
                isExpired.shouldBeFalse()
            }
        }

        When("when after expiry date") {
            val EXPIRY_DAYS = 7L
            val createdOn = ZonedDateTime.now().minusDays(10).toInstant().toEpochMilli()

            Then("should return true for expiry") {
                val isExpired = fileUtils.isOlderThanDuration(createdOn, EXPIRY_DAYS)
                isExpired.shouldBeTrue()
            }
        }

        When("when on expiry date") {
            val EXPIRY_DAYS = 7L
            val createdOn = ZonedDateTime.now().minusDays(10).toInstant().toEpochMilli()

            Then("should return true for expiry") {
                val isExpired = fileUtils.isOlderThanDuration(createdOn, EXPIRY_DAYS)
                isExpired.shouldBeTrue()
            }
        }
    }
})