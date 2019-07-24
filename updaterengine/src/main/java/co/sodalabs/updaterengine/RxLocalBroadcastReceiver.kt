package co.sodalabs.privilegedinstaller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import io.reactivex.Observable
import timber.log.Timber

/**
 * Wrap the [BroadcastReceiver] in the Reactive way.
 */
object RxLocalBroadcastReceiver {

    @JvmStatic
    fun bind(
        context: Context,
        intentFilter: IntentFilter
    ): Observable<Intent> {
        return Observable.create { e ->
            val manager = LocalBroadcastManager.getInstance(context)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (e.isDisposed) return
                    // Timber.d("Receive $intent from the local broadcast for $context")
                    e.onNext(intent)
                }
            }

            e.setCancellable {
                // Timber.d("Unregister the local broadcast listening from $context")
                try {
                    manager.unregisterReceiver(receiver)
                } catch (err: Throwable) {
                    Timber.e(err)
                }
            }

            manager.registerReceiver(receiver, intentFilter)
        }
    }
}