package co.sodalabs.apkupdater.feature.heartbeat

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import co.sodalabs.updaterengine.UpdaterHeartBeater
import co.sodalabs.updaterengine.UpdaterJobs
import dagger.android.AndroidInjection
import javax.inject.Inject

/**
 * Interface between the new {@link android.app.job.JobScheduler} API and
 * {@link UpdateService}, which is based on {@link android.support.v4.app.JobIntentService}.
 *
 * @see <a href="https://developer.android.com/about/versions/android-5.0.html#Power">Project Volta: Scheduling jobs</a>
 */
@TargetApi(21)
class HeartBeatJobService : JobService() {

    @Inject
    lateinit var heartbeater: UpdaterHeartBeater

    override fun onCreate() {
        AndroidInjection.inject(this)
        super.onCreate()
    }

    override fun onStartJob(params: JobParameters): Boolean {
        when (params.jobId) {
            UpdaterJobs.JOB_ID_HEART_BEAT -> sendHeartbeat()
        }

        // TODO: Shall we return true and call jobFinished()?
        // TODO: https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    private fun sendHeartbeat() {
        heartbeater.sendHeartBeatNow()
    }
}