package co.sodalabs.updaterengine.feature.installer

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.PersistableBundle
import co.sodalabs.updaterengine.UpdaterJobs
import timber.log.Timber

/**
 * Interface between the new {@link android.app.job.JobScheduler} API and
 * {@link UpdateService}, which is based on {@link android.support.v4.app.JobIntentService}.
 *
 * @see <a href="https://developer.android.com/about/versions/android-5.0.html#Power">Project Volta: Scheduling jobs</a>
 */
@TargetApi(21)
class InstallerJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        val extras = params.extras
        when (params.jobId) {
            UpdaterJobs.JOB_ID_INSTALL_UPDATES -> installUpdates(extras)
            else -> Timber.e("Hey develop, this JobService is for checking the updates only")
        }

        // TODO: Shall we return true and call jobFinished()?
        // TODO: https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        return true
    }

    private fun installUpdates(
        extras: PersistableBundle
    ) {
        // FIXME: Implement this for API >= 21
        // val updates = extras.getStringArray(IntentActions.PROP_DOWNLOADED_UPDATES)
        //     ?: throw IllegalArgumentException("Must provide a updates list.")
        //
        // InstallerJobIntentService.installAppUpdateNow(
        //     context = this,
        //     downloadedUpdates = updates)
        TODO()
    }
}