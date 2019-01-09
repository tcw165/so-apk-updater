package co.sodalabs.updaterengine

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService

/**
 * Interface between the new {@link android.app.job.JobScheduler} API and
 * {@link UpdateService}, which is based on {@link android.support.v4.app.JobIntentService}.
 *
 * @see <a href="https://developer.android.com/about/versions/android-5.0.html#Power">Project Volta: Scheduling jobs</a>
 */
@TargetApi(21)
class UpdateJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        val updateUrl = extras.getString(UpdaterService.EXTRA_UPDATE_URL)
            ?: throw IllegalArgumentException("Must provide update url.")
        UpdaterService.checkNow(this, updateUrl)
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }
}