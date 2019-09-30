package co.sodalabs.updaterengine

import android.annotation.TargetApi
import android.app.job.JobParameters
import android.app.job.JobService
import android.os.PersistableBundle
import co.sodalabs.updaterengine.extension.toBoolean
import co.sodalabs.updaterengine.extension.toInt
import timber.log.Timber

/**
 * Interface between the new {@link android.app.job.JobScheduler} API and
 * {@link UpdateService}, which is based on {@link android.support.v4.app.JobIntentService}.
 *
 * @see <a href="https://developer.android.com/about/versions/android-5.0.html#Power">Project Volta: Scheduling jobs</a>
 */
@TargetApi(21)
class UpdaterJobService : JobService() {

    override fun onStartJob(
        params: JobParameters
    ): Boolean {
        val extras = params.extras
        when (params.jobId) {
            UpdaterJobs.JOB_ID_ENGINE_TRANSITION_TO_CHECK -> checkNow(extras)
            else -> Timber.e("Hey develop, this JobService is for checking the updates only")
        }

        // TODO: Shall we return true and call jobFinished()?
        // TODO: https://medium.com/google-developers/scheduling-jobs-like-a-pro-with-jobscheduler-286ef8510129
        return false
    }

    override fun onStopJob(
        params: JobParameters
    ): Boolean {
        return true
    }

    private fun checkNow(
        extras: PersistableBundle
    ) {
        // Can't pass boolean cause that's implemented >= 22.
        val resetSession = extras.getInt(IntentActions.PROP_RESET_UPDATER_SESSION, false.toInt()).toBoolean()
        UpdaterService.checkUpdateNow(this, resetSession)
    }
}