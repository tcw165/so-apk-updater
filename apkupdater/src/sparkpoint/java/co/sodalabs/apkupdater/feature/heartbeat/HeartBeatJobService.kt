package co.sodalabs.apkupdater.feature.heartbeat

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import co.sodalabs.updaterengine.UpdaterJobs

/**
 * Interface between the new {@link android.app.job.JobScheduler} API and
 * {@link UpdateService}, which is based on {@link android.support.v4.app.JobIntentService}.
 *
 * @see <a href="https://developer.android.com/about/versions/android-5.0.html#Power">Project Volta: Scheduling jobs</a>
 */
@TargetApi(21)
class HeartBeatJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        when (params.jobId) {
            UpdaterJobs.JOB_ID_HEART_BEAT -> checkVersions()
        }

        // TODO: Shall we return true and call jobFinished()?
        // TODO: https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    private fun checkVersions() {
        HeartBeatService.checkUpdatesNow(this)
    }
}