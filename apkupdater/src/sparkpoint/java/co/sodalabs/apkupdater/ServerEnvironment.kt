package co.sodalabs.apkupdater

enum class ServerEnvironment {
    LOCALHOST,
    STAGING,
    PRODUCTION;

    companion object {

        fun fromRawUrl(rawUrlOpt: String?): ServerEnvironment {
            val rawUrl = rawUrlOpt ?: return LOCALHOST

            return when {
                rawUrl.contains("staging") -> STAGING
                rawUrl.contains("localhost") -> LOCALHOST
                else -> PRODUCTION
            }
        }
    }
}
