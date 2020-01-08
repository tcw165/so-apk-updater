package co.sodalabs.updaterengine

import io.reactivex.Single
import java.util.UUID

interface IWorkObserver {
    fun observeWorkSuccessByID(id: UUID): Single<Boolean>
}