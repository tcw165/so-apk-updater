package co.sodalabs.privilegedinstaller

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import co.sodalabs.privilegedinstaller.exceptions.RxServiceConnectionError
import io.reactivex.Observable
import timber.log.Timber

/**
 * Binds and unbinds [Service] via [Observable].
 */
object RxServiceConnection {

    @JvmStatic
    @JvmOverloads
    fun bind(
        context: Context,
        intent: Intent,
        flag: Int = Context.BIND_AUTO_CREATE
    ): Observable<IBinder> {
        var bound = false

        return Observable.create { e ->
            val serviceConnection = object : ServiceConnection {
                override fun onServiceConnected(componentName: ComponentName, binder: IBinder) {
                    if (e.isDisposed) return

                    Timber.d("onServiceConnected: ${componentName.className}")
                    e.onNext(binder)
                }

                override fun onServiceDisconnected(componentName: ComponentName) {
                    bound = false

                    if (!e.isDisposed) {
                        Timber.d("onServiceDisconnected: ${componentName.className}")
                        val componentNameString = componentName.flattenToString() ?: ""
                        e.onError(RxServiceConnectionError(componentNameString))
                    }
                }
            }

            e.setCancellable {
                if (bound) {
                    context.unbindService(serviceConnection)
                }
            }

            // start the service
            bound = context.bindService(intent, serviceConnection, flag)
            if (!bound) {
                val componentName = intent.component?.flattenToString() ?: ""
                e.onError(RxServiceConnectionError(componentName))
            }
        }
    }
}