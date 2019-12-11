package co.sodalabs.updaterengine.utils

import co.sodalabs.updaterengine.IThreadSchedulers
import io.kotlintest.specs.BehaviorSpec
import io.mockk.mockk
import org.amshove.kluent.shouldEqual

const val TEST_APPLICATION_TAG = "com.test.app"
const val TEST_WHITELIST_ITEM = 999
const val TEST_BLACKLIST_ITEM = 998

class AdbUtilsTestSpec : BehaviorSpec({

    val file by lazy { createTempFile() }

    Given("a ADB helper utility") {

        val mockScheduler = mockk<IThreadSchedulers>()
        val mockProcessInfoProvider = mockk<ProcessInfoProvider>()
        val adbUtils = AdbUtils(mockScheduler, mockProcessInfoProvider)

        When("all parameters are provided") {
            val applicationTag: String = TEST_APPLICATION_TAG
            val whiteList = listOf(TEST_WHITELIST_ITEM, TEST_WHITELIST_ITEM)
            val blackList = listOf(TEST_BLACKLIST_ITEM, TEST_BLACKLIST_ITEM)

            val cmd = adbUtils.generateCommand(file, applicationTag, DEFAULT_MAX_LOG_COUNT, whiteList, blackList)

            Then("generated command should be correct") {
                val expectedCommand = "logcat -v time -t $DEFAULT_MAX_LOG_COUNT -f ${file.absolutePath} prune '$TEST_WHITELIST_ITEM $TEST_WHITELIST_ITEM ~$TEST_BLACKLIST_ITEM ~$TEST_BLACKLIST_ITEM' $TEST_APPLICATION_TAG:I"
                cmd.joinToString(" ").shouldEqual(expectedCommand)
            }
        }

        When("application is not specified") {
            val whiteList = listOf(TEST_WHITELIST_ITEM, TEST_WHITELIST_ITEM)
            val blackList = listOf(TEST_BLACKLIST_ITEM, TEST_BLACKLIST_ITEM)

            val cmd = adbUtils.generateCommand(
                file = file,
                maxLineCount = DEFAULT_MAX_LOG_COUNT,
                whiteListPids = whiteList,
                blackListPids = blackList
            )

            Then("generated command should not contain application filter") {
                val expectedCommand = "logcat -v time -t $DEFAULT_MAX_LOG_COUNT -f ${file.absolutePath} prune '$TEST_WHITELIST_ITEM $TEST_WHITELIST_ITEM ~$TEST_BLACKLIST_ITEM ~$TEST_BLACKLIST_ITEM'"
                cmd.joinToString(" ").shouldEqual(expectedCommand)
            }
        }

        When("whitelist, blacklist and application are not specified") {

            val cmd = adbUtils.generateCommand(file = file)

            Then("generated command should not contain missing items") {
                val expectedCommand = "logcat -v time -t $DEFAULT_MAX_LOG_COUNT -f ${file.absolutePath} prune ''"
                cmd.joinToString(" ").shouldEqual(expectedCommand)
            }
        }

        When("blacklist and application are not specified") {
            val whiteList = listOf(TEST_WHITELIST_ITEM, TEST_WHITELIST_ITEM)

            val cmd = adbUtils.generateCommand(file = file, whiteListPids = whiteList)

            Then("generated command should not contain missing items") {
                val expectedCommand = "logcat -v time -t $DEFAULT_MAX_LOG_COUNT -f ${file.absolutePath} prune '$TEST_WHITELIST_ITEM $TEST_WHITELIST_ITEM'"
                cmd.joinToString(" ").shouldEqual(expectedCommand)
            }
        }

        When("whitelist and application are not specified") {
            val blackList = listOf(TEST_BLACKLIST_ITEM, TEST_BLACKLIST_ITEM)

            val cmd = adbUtils.generateCommand(file = file, blackListPids = blackList)

            Then("generated command should not contain missing items") {
                val expectedCommand = "logcat -v time -t $DEFAULT_MAX_LOG_COUNT -f ${file.absolutePath} prune '~$TEST_BLACKLIST_ITEM ~$TEST_BLACKLIST_ITEM'"
                cmd.joinToString(" ").shouldEqual(expectedCommand)
            }
        }
    }
})