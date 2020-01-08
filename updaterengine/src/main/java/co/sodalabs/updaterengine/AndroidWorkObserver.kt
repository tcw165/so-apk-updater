package co.sodalabs.updaterengine

import android.content.Context
import androidx.work.WorkInfo
import androidx.work.WorkManager
import co.sodalabs.updaterengine.utils.getWorkInfoByIdObservable
import io.reactivex.Single
import java.util.UUID
import javax.inject.Inject

class AndroidWorkObserver @Inject constructor(
    private val context: Context
) : IWorkObserver {

    private val workManager by lazy { WorkManager.getInstance(context) }

    override fun observeWorkSuccessByID(
        id: UUID
    ): Single<Boolean> {
        return workManager.getWorkInfoByIdObservable(id)
            .filter {
                it.state == WorkInfo.State.SUCCEEDED || it.state == WorkInfo.State.FAILED
            }
            .map { it.state == WorkInfo.State.SUCCEEDED }
            .firstOrError()
    }
}