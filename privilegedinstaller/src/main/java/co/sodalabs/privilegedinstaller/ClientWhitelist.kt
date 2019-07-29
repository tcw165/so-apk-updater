package co.sodalabs.privilegedinstaller

import java.util.Arrays

/**
 * Only apps signed using a certificate with a SHA-256 hash listed here
 * can access the Privileged Extension!
 * <ol>
 * <li>Get SHA-256 of certificate as lowercase without colons with
 * <code>keytool -printcert -jarfile com.example.apk | sed -n 's,SHA256:\s*\([A-F0-9:]*\),\1,p' | sed 's,:,,g'
 * | tr A-f a-f</code></li>
 * <li>Add here with Application ID</li>
 * </ol>
 */
object ClientWhitelist {

    val whitelist: HashSet<Pair<String, String>> = HashSet(
        Arrays.asList(
            // FIXME: Put the platform key here!
            // Certificate SHA-256 of co.sodalabs.apkupdater and co.sodalabs.sparkpoint
            Pair("co.sodalabs.apkupdater", "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8"),
            Pair("co.sodalabs.sparkpoint", "c8a2e9bccf597c2fb6dc66bee293fc13f2fc47ec77bc6b2b0d52c11f51192ab8")
        )
    )
}