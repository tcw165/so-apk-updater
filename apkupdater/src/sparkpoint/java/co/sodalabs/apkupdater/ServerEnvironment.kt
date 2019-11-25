package co.sodalabs.apkupdater

@Deprecated("Use [SPARKPOINT_REST_API_BASE_URL] instead")
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
