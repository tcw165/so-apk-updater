package co.sodalabs.updaterengine

object UpdaterJobs {
    const val JOB_ID_ENGINE_TRANSITION_TO_CHECK = 0xfedcb0
    const val JOB_ID_CHECK_UPDATES = 0xfedcb1
    const val JOB_ID_DOWNLOAD_UPDATES = 0xfedcb2
    const val JOB_ID_INSTALL_UPDATES = 0xfedcb3
    const val JOB_ID_HEART_BEAT = 0xfedcb4

    const val JOB_ACTION = "$ACTION_PREFIX.job_action"
}