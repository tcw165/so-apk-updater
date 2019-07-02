package co.sodalabs.apkupdater

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import co.sodalabs.privilegedinstaller.RxLocalBroadcastReceiver
import co.sodalabs.updaterengine.AppUpdatesChecker
import co.sodalabs.updaterengine.IThreadSchedulers
import co.sodalabs.updaterengine.IntentActions
import co.sodalabs.updaterengine.data.AppUpdate
import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import javax.inject.Inject

@Suppress("unused")
private const val PACKAGE_SPARK_POINT = "co.sodalabs.sparkpoint"

class SparkPointAppUpdatesChecker @Inject constructor(
    private val context: Context,
    private val schedulers: IThreadSchedulers
) : AppUpdatesChecker {

    // TODO: Call the UpdatesCheckerService and get result from the broadcast!
    override fun checkNow(
        packageNames: List<String>
    ): Single<List<AppUpdate>> {
        // Install action.
        val installAction = launchCheckService(packageNames)
        // Install result.
        val intentFilter = IntentFilter()
        intentFilter.addAction(IntentActions.ACTION_UPDATES_FOUND)
        intentFilter.addAction(IntentActions.ACTION_UPDATES_NOT_FOUND)
        val installResultReceiver = RxLocalBroadcastReceiver.bind(context, intentFilter)
            .map { extractUpdates(it) }

        return Observable.merge(
            installResultReceiver,
            installAction.toObservable())
            .firstOrError()
    }

    override fun scheduleCheck(
        beginClock24H: Int,
        endClock24H: Int
    ): Observable<List<AppUpdate>> {
        TODO("not implemented")
    }

    private fun launchCheckService(
        packageNames: List<String>
    ): Completable {
        return Completable
            .fromAction {
                UpdatesCheckerService.checkUpdatesNow(
                    context,
                    packageNames.toTypedArray())
            }
            .subscribeOn(schedulers.main())
    }

    private fun extractUpdates(
        intent: Intent
    ): List<AppUpdate> {
        return when (intent.action) {
            IntentActions.ACTION_UPDATES_FOUND -> {
                val things = intent.getParcelableArrayExtra(IntentActions.PROP_APP_UPDATES)
                val updates = mutableListOf<AppUpdate>()
                for (i in 0 until things.size) {
                    val thing = things[i]
                    if (thing is AppUpdate) {
                        updates.add(thing)
                    }
                }
                updates
            }
            IntentActions.ACTION_UPDATES_NOT_FOUND -> emptyList()
            else -> throw IllegalArgumentException("Hey develop, you might mess up the code!")
        }
    }
}